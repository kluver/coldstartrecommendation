import coldstartrecommendation.BaselineMixingItemScorer
import coldstartrecommendation.WeightSymbol
import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.baseline.*
import org.grouplens.lenskit.eval.data.crossfold.CrossfoldMethod
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder
import org.grouplens.lenskit.eval.metrics.predict.*
import org.grouplens.lenskit.eval.metrics.topn.ItemSelectors
import org.grouplens.lenskit.eval.metrics.topn.ItemSimilarityMetric
import org.grouplens.lenskit.iterative.IterationCount
import org.grouplens.lenskit.knn.NeighborhoodSize
import org.grouplens.lenskit.knn.item.ItemItemScorer
import org.grouplens.lenskit.knn.item.ItemSimilarity
import org.grouplens.lenskit.knn.item.ItemSimilarityThreshold
import org.grouplens.lenskit.knn.item.ModelSize
import org.grouplens.lenskit.knn.item.WeightedAverageNeighborhoodScorer
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext
import org.grouplens.lenskit.knn.item.model.ItemItemModel
import org.grouplens.lenskit.knn.user.UserUserItemScorer
import org.grouplens.lenskit.mf.funksvd.FeatureCount
import org.grouplens.lenskit.mf.funksvd.FunkSVDItemScorer
import org.grouplens.lenskit.symbols.Symbol
import org.grouplens.lenskit.transform.normalize.BaselineSubtractingUserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.transform.threshold.NoThreshold
import org.grouplens.lenskit.transform.threshold.Threshold
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity

//def sizes = [0,2,4,8,12,16,19,32,64,128]
//def sizes = [0,2,4,8,16,19]
def sizes = [0,3,9,18]

// This target unpacks the data
sourceDataset100k = target('download100k') {

    def zipFile = "${config.dataDir}/ml-100k.zip"
    def dataDir = config.get('mldata.directory', "${config.dataDir}/ml-100k")

    ant.mkdir(dir: config.dataDir)
    ant.get(src: 'http://www.grouplens.org/system/files/ml-100k.zip',
            dest: zipFile,
            skipExisting: true)
    ant.unzip(src: zipFile, dest: dataDir) {
        patternset {
            include name: 'ml-100k/*'
        }
        mapper type: 'flatten'
    }
    perform {
        return csvfile("${dataDir}/u.data") {
            delimiter "\t"
            domain {
                minimum 1
                maximum 5
                precision 1.0
            }
        }
    }
}

// This target unpacks the data
sourceDataset1m = target('download1m') {
    def zipFile = "${config.dataDir}/ml-1m.zip"
    def dataDir = config.get('mldata.directory', "${config.dataDir}/ml-1m")

    ant.mkdir(dir: config.dataDir)
    ant.get(src: 'http://files.grouplens.org/datasets/movielens/ml-1m.zip',
            dest: zipFile,
            skipExisting: true)
    ant.unzip(src: zipFile, dest: dataDir) {
        patternset {
            include name: 'ml-1m/*'
        }
        mapper type: 'flatten'
    }
    perform {
        csvfile("${dataDir}/ratings.dat") {
            delimiter "::"
            domain {
                minimum 1
                maximum 5
                precision 0.5
            }
        }
    }
}

// This target unpacks the data
sourceDataset10m = target('download10m') {
    def zipFile = "${config.dataDir}/ml-10m.zip"
    def dataDir = config.get('mldata.directory', "${config.dataDir}/ml-10M100K")

    ant.mkdir(dir: config.dataDir)
    ant.get(src: 'http://files.grouplens.org/datasets/movielens/ml-10m.zip',
            dest: zipFile,
            skipExisting: true)
    ant.unzip(src: zipFile, dest: dataDir) {
        patternset {
            include name: 'ml-10M100K/*'
        }
        mapper type: 'flatten'
    }
    perform {
        csvfile("${dataDir}/ratings.dat") {
            delimiter "::"
            domain {
                minimum 1
                maximum 5
                precision 0.5
            }
        }
    }
}

def sourceDataset = sourceDataset1m

def datasets = target('do-crossfolds') {
    requires sourceDataset
    
    def data = []
    for (i in sizes) {
        d = /*pack {
            dataset*/ crossfold (""+i) {
                source sourceDataset
                test "${config.dataDir}/${sourceDataset.name}-crossfold/test"+i+".%d.csv"
                train "${config.dataDir}/${sourceDataset.name}-crossfold/train"+i+".%d.csv"
                order RandomOrder
                retain i
                partitions 5
                method CrossfoldMethod.SAMPLE_USERS
                sampleSize 100
            //}
            //includeTimestamps false
        }
        data +=d
    }
    perform {
        result = []
        for (d in data) {
            result += d.get()
        }
        return result
    }
}

target('evaluate') {
    // this requires the ml100k target to be run first
    // can either reference a target by object or by name (as above)
    requires datasets
    
    trainTest {
        dataset datasets

        componentCacheDirectory "${config.analysisDir}/cache"

        // Three different types of output for analysis.
        output "${config.analysisDir}/eval-results.csv"
        predictOutput "${config.analysisDir}/eval-preds.csv"
        userOutput "${config.analysisDir}/eval-user.csv"

        metric CoveragePredictMetric
        metric RMSEPredictMetric
        metric MAEPredictMetric
        metric NDCGPredictMetric
        metric EntropyPredictMetric

        def topNConfig = {
            listSize 10
            candidates ItemSelectors.allItems()
            exclude ItemSelectors.trainingItems()
        }

        metric topNnDCG {
            listSize 10
            candidates ItemSelectors.addNRandom(ItemSelectors.testItems(), 1000)
            exclude ItemSelectors.trainingItems()
        }
        metric (topNLength(topNConfig))
        metric (topNPopularity(topNConfig))
        metric (topNDiversity(topNConfig))
        
        def diversityConfig = {
            root (ItemSimilarityMetric)
            within (ItemSimilarityMetric) {
                bind VectorSimilarity to CosineVectorSimilarity
                bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
                within (UserVectorNormalizer) {
                    bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
                    set MeanDamping to 5.0d
                }
            }
        }
        
        algorithm("ItemBaseline") {
            bind ItemScorer to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
            include diversityConfig
        }

        algorithm("UserItemBaseline") {
            bind ItemScorer to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer)to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
            include diversityConfig
        }

        algorithm("ItemItem") {
            bind ItemScorer to ItemItemScorer
            within (ItemScorer) {
                bind VectorSimilarity to CosineVectorSimilarity
                bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
                within (UserVectorNormalizer) {
                    bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
                    set MeanDamping to 5.0d
                }
                set ModelSize to 250
                set NeighborhoodSize to 50
            }
            bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
            
            include diversityConfig
        }
        
        
        algorithm("UserUser") {
            bind ItemScorer to UserUserItemScorer
            bind VectorSimilarity to CosineVectorSimilarity
            bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
            set MeanDamping to 5.0d
            set ModelSize to 250
            set NeighborhoodSize to 50

            include diversityConfig
        }

        algorithm("ScaledUserUser") {
            bind ItemScorer to BaselineMixingItemScorer
            within (BaselineMixingItemScorer) {
                bind ItemScorer to UserUserItemScorer
                bind VectorSimilarity to CosineVectorSimilarity
                bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
                set ModelSize to 250
                set NeighborhoodSize to 50
            }
            bind (WeightSymbol, Symbol) to UserUserItemScorer.NEIGHBORHOOD_WEIGHT_SYMBOL
            bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d

            include diversityConfig
        }

        algorithm("ScaledItemItem") {
            bind ItemScorer to BaselineMixingItemScorer
            within (ItemScorer) {
                bind ItemScorer to ItemItemScorer
                bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
                bind VectorSimilarity to CosineVectorSimilarity
                within (UserVectorNormalizer) {
                    bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
                    set MeanDamping to 5.0d
                }
                // retain 500 neighbors in the model, use 30 for prediction
                set ModelSize to 250
                set NeighborhoodSize to 50
            }
            bind (WeightSymbol, Symbol) to WeightedAverageNeighborhoodScorer.NEIGHBORHOOD_WEIGHT_SYMBOL
            bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
            include diversityConfig
        }
        
        algorithm("svd") {
            bind ItemScorer to FunkSVDItemScorer
            bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
            set FeatureCount to 25
            set IterationCount to 125
            include diversityConfig
        }
        
        writePredictionChannel(ItemItemScorer.NEIGHBORHOOD_SIZE_SYMBOL,"nhbd")
        writePredictionChannel(WeightedAverageNeighborhoodScorer.NEIGHBORHOOD_WEIGHT_SYMBOL, "simsum")
    }
}

/*target('dump') {
    dumpGraph {
        output "graph.dot"
        algorithm uu
    }
}*/
    
// After running the evaluation, let's analyze the results
target('analyze') {
    requires 'evaluate'
    //requires 'dump'
    // Run R. Note that the script is run in the analysis directory; you might want to
    // copy all R scripts there instead of running them from the source dir.
    ant.exec(executable: config["rscript.executable"], dir: config.analysisDir) {
        arg value: "${config.scriptDir}/chart.R"
    }
}

// By default, run the analyze target
defaultTarget 'analyze'