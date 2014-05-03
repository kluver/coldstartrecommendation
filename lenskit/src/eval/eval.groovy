import coldstartrecommendation.BaselineMixingItemScorer
import coldstartrecommendation.DownsampleDataSet
import coldstartrecommendation.ItemScorerCoveragePredictMetric
import coldstartrecommendation.MixingWeight
import coldstartrecommendation.OracleItemScorer
import coldstartrecommendation.OracleItemScorer
import coldstartrecommendation.TopNMapMetric
import coldstartrecommendation.TopNRMSEMetric
import coldstartrecommendation.WeightSymbol
import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.baseline.*
import org.grouplens.lenskit.eval.data.DataSource
import org.grouplens.lenskit.eval.data.crossfold.CrossfoldMethod
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder
import org.grouplens.lenskit.eval.metrics.predict.*
import org.grouplens.lenskit.eval.metrics.topn.IndependentRecallTopNMetric
import org.grouplens.lenskit.eval.metrics.topn.ItemSelectors
import ItemSimilarityMetric
import org.grouplens.lenskit.eval.metrics.topn.MRRTopNMetric
import org.grouplens.lenskit.eval.metrics.topn.PrecisionRecallTopNMetric
import org.grouplens.lenskit.iterative.IterationCount
import org.grouplens.lenskit.knn.NeighborhoodSize
import org.grouplens.lenskit.knn.item.ItemItemScorer
import org.grouplens.lenskit.knn.item.ItemSimilarity
import org.grouplens.lenskit.knn.item.ItemSimilarityThreshold
import org.grouplens.lenskit.knn.item.ModelSize
import org.grouplens.lenskit.knn.item.WeightedAverageNeighborhoodScorer
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext
import org.grouplens.lenskit.knn.item.model.ItemItemModel
import org.grouplens.lenskit.knn.user.NeighborFinder
import org.grouplens.lenskit.knn.user.SnapshotNeighborFinder
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
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.collection.IsIn

//def sizes = [0,1,2,3]
//def sizes = [0,8,16,32,128]
//def sizes = [0,1,2,4,8,12,16,19]
def sizes = [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19]
//def sizes = [0,2,4,8,16,19]
//def sizes = [0,2,4,8]
//def sizes = [0,3,9,18]
//def sizes = [0,1,2,4,8]

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
                precision 1
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
                minimum 0.5
                maximum 5
                precision 0.5
            }
        }
    }
}

def sourceDataset = sourceDataset1m

def crossfolds = target('make-original-crossfold') {
    def size = sizes.max()
    requires sourceDataset
    crossfold() {
        source sourceDataset
        test "${config.dataDir}/${sourceDataset.name}-crossfold/test"+size+".%d.csv"
        train "${config.dataDir}/${sourceDataset.name}-crossfold/train"+size+".%d.csv"
        order RandomOrder
        retain size
        partitions 5
    }
}

def datasets = target('subsample-crossfold') {
    requires crossfolds
    perform {
        d = []
        for (i in sizes) {
            def downsample =  new DownsampleDataSet(""+i)
                    .setDirectory("${config.dataDir}/${sourceDataset.name}-crossfold/")
                    .setRetain(i)
                    .setSources(crossfolds.get())
            downsample.execute()
            d += downsample.get()
        }
        return d
    }
}/**/

tmp = target("tmp") {
    requires sourceDataset
    perform {
        return algorithm("Oracle")  {
            bind ItemScorer to OracleItemScorer;
            set MeanDamping to 1.0d
            within ItemScorer bind DataSource to sourceDataset.get()
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
    }
}

target('evaluate') {
    // this requires the ml100k target to be run first
    // can either reference a target by object or by name (as above)
    requires datasets
    //requires tmp
    
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

        metric ItemScorerCoveragePredictMetric;
        
        def topNConfig = {
            listSize 20
            candidates ItemSelectors.allItems()
            exclude ItemSelectors.trainingItems()
        }

        metric (topNLength(topNConfig))
        metric (topNPopularity(topNConfig))
        metric (topNEntropy(topNConfig))
        metric new TopNDiversityMetric.Builder()
                .setListSize(20)
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .build();/**/
        metric new TopNRMSEMetric.Builder()
                .setListSize(20)
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .build();/**/
        metric new PrecisionRecallTopNMetric.Builder()
                .setListSize(20)
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(4.0d)))
                .build();
        metric new PrecisionRecallTopNMetric.Builder()
                .setListSize(20)
		.setSuffix("5")
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(5.0d)))
                .build();

        metric new PrecisionRecallTopNMetric.Builder()
                .setListSize(20)
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setSuffix("fallout")
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.lessThanOrEqualTo(2.0d)))
                .build();/**/

        metric new PrecisionRecallTopNMetric.Builder()
                .setListSize(20)
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setSuffix("fallout1")
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.lessThanOrEqualTo(1.0d)))
                .build();/**/
        /*metric new MRRTopNMetric.Builder()
                .setListSize(20)
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(5.0d)))
                .build();/**/
        /*metric new MRRTopNMetric.Builder()
                .setListSize(20)
		.setSuffix("5")
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(4.0d)))
                .build();/**/
        metric new TopNMapMetric.Builder()
                .setListSize(20)
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(4.0d)))
                .build();/**/
        metric new TopNMapMetric.Builder()
                .setListSize(20)
		.setSuffix("5")
                .setCandidates(ItemSelectors.allItems())
                .setExclude(ItemSelectors.trainingItems())
                .setGoodItems(ItemSelectors.testRatingMatches(Matchers.greaterThanOrEqualTo(5.0d)))
                .build();/**/
        
        
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
                set ModelSize to 500
                set NeighborhoodSize to 30
            }
            bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
            
            include diversityConfig
        }/**/

        algorithm("UserUser") {
            bind ItemScorer to UserUserItemScorer
            bind VectorSimilarity to CosineVectorSimilarity
            bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            bind NeighborFinder to SnapshotNeighborFinder
            bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
            set MeanDamping to 5.0d
            set NeighborhoodSize to 30

            include diversityConfig
        }/**/
        
        algorithm("svd") {
            bind ItemScorer to FunkSVDItemScorer
            bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
            set FeatureCount to 30
            set IterationCount to 100
            include diversityConfig
        }/**/

        //algorithm tmp
        
        writePredictionChannel(ItemItemScorer.NEIGHBORHOOD_SIZE_SYMBOL,"inhbd")
        writePredictionChannel(UserUserItemScorer.NEIGHBORHOOD_SIZE_SYMBOL,"unhbd")
        writePredictionChannel(WeightedAverageNeighborhoodScorer.NEIGHBORHOOD_WEIGHT_SYMBOL, "isimsum")
        writePredictionChannel(UserUserItemScorer.NEIGHBORHOOD_WEIGHT_SYMBOL, "usimsum")
        
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