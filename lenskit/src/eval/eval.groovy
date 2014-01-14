import coldstartrecommendation.OracleRatingPredictor
import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.RatingPredictor
import org.grouplens.lenskit.data.dao.EventDAO
import org.grouplens.lenskit.eval.data.DataSource
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder
import org.grouplens.lenskit.eval.metrics.predict.CoveragePredictMetric
import org.grouplens.lenskit.eval.metrics.predict.EntropyPredictMetric
import org.grouplens.lenskit.eval.metrics.predict.MAEPredictMetric
import org.grouplens.lenskit.eval.metrics.predict.NDCGPredictMetric
import org.grouplens.lenskit.eval.metrics.predict.RMSEPredictMetric
import org.grouplens.lenskit.knn.NeighborhoodSize
import org.grouplens.lenskit.knn.item.*
import org.grouplens.lenskit.knn.user.*
import org.grouplens.lenskit.baseline.*
import org.grouplens.lenskit.transform.normalize.*

import org.apache.commons.lang3.BooleanUtils
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity

def MAX=19

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

def sourceDataset = sourceDataset1m

def datasets = target('do-crossfolds') {
    requires sourceDataset
    
    def data = []
    for (i in (0 .. 9).collect{it*2}) {
        d = crossfold (""+i) {
            source sourceDataset
            test "${config.dataDir}/ml-1m-crossfold/test"+i+".%d.csv"
            train "${config.dataDir}/ml-1m-crossfold/train"+i+".%d.csv"
            order RandomOrder
            retain i
            partitions 5
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
        
        
        // Three different types of output for analysis.
        output "${config.analysisDir}/eval-results.csv"
        predictOutput "${config.analysisDir}/eval-preds.csv"
        userOutput "${config.analysisDir}/eval-user.csv"

        metric CoveragePredictMetric
        metric RMSEPredictMetric
        metric MAEPredictMetric
        metric NDCGPredictMetric
        metric EntropyPredictMetric

        algorithm("UserItemBaseline5") {
            bind ItemScorer to UserMeanItemScorer
            bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
        }
        
        algorithm("ItemBaseline5") {
            bind ItemScorer to ItemMeanRatingItemScorer
            set MeanDamping to 5.0d
        }

        algorithm("ItemItemItemBaseLine") {
            // use the item-item rating predictor with a baseline and normalizer
            bind ItemScorer to ItemItemScorer
            bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
            bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
            bind VectorSimilarity to CosineVectorSimilarity

            // retain 500 neighbors in the model, use 30 for prediction
            set ModelSize to 250
            set NeighborhoodSize to 50

            // apply some Bayesian smoothing to the mean values
            within(BaselineScorer, ItemScorer) {
                set MeanDamping to 5.0d
            }
        }

        algorithm("ItemItemNoBaseLine") {
            // use the item-item rating predictor with a baseline and normalizer
            bind ItemScorer to ItemItemScorer
            bind VectorSimilarity to CosineVectorSimilarity
            //bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
            //bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
            //bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer

            // retain 500 neighbors in the model, use 30 for prediction
            set ModelSize to 250
            set NeighborhoodSize to 50

            // apply some Bayesian smoothing to the mean values
            //within(BaselineScorer, ItemScorer) {
            //    set MeanDamping to 25.0d
            //}
        }
        //algorithm oracle
        //algorithm useruser
    }
}

// After running the evaluation, let's analyze the results
target('analyze') {
    requires 'evaluate'
    // Run R. Note that the script is run in the analysis directory; you might want to
    // copy all R scripts there instead of running them from the source dir.
    ant.exec(executable: config["rscript.executable"], dir: config.analysisDir) {
        arg value: "${config.scriptDir}/chart.R"
    }
}

// By default, run the analyze target
defaultTarget 'analyze'