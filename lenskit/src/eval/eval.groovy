import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder

import org.grouplens.lenskit.knn.NeighborhoodSize
import org.grouplens.lenskit.knn.item.*
import org.grouplens.lenskit.knn.user.*
import org.grouplens.lenskit.baseline.*
import org.grouplens.lenskit.transform.normalize.*

import org.apache.commons.lang3.BooleanUtils

def zipFile = "${config.dataDir}/ml100k.zip"
def dataDir = config.get('mldata.directory', "${config.dataDir}/ml100k")
def MAX=20

// This target unpacks the data
target('download') {
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
}

data = []

def datasets = target('do-crossfolds') {
    requires 'download'
    for (i in 0..MAX) {
        d = crossfold (""+i) {
            source csvfile("${dataDir}/u.data") {
                delimiter "\t"
                domain {
                    minimum 1.0
                    maximum 5.0
                    precision 1.0
                }
            }
            test "${config.dataDir}/ml100k-crossfold/test"+i+".%d.csv"
            train "${config.dataDir}/ml100k-crossfold/train"+i+".%d.csv"
            order RandomOrder
            retain i
            partitions 5
        }
        data +=d
    }
    data
}

// Let's define some algorithms
def itemitem = algorithm("ItemItem") {
    // use the item-item rating predictor with a baseline and normalizer
    bind ItemScorer to ItemItemScorer
    bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
    bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
    bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
    
    // retain 500 neighbors in the model, use 30 for prediction
    set ModelSize to 250
    set NeighborhoodSize to 50

    // apply some Bayesian smoothing to the mean values
    within(BaselineScorer, ItemScorer) {
        set MeanDamping to 25.0d
    }
}

def useruser = algorithm("UserUser") {
    // use the user-user rating predictor
    bind ItemScorer to UserUserItemScorer
    bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
    bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
    bind VectorNormalizer to MeanVarianceNormalizer

    // use 30 neighbors for predictions
    set NeighborhoodSize to 30

    // override normalizer within the neighborhood finder
    // this makes it use a different normalizer (subtract user mean) for computing
    // user similarities
    within(NeighborhoodFinder) {
        bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
        // override baseline to use user mean
        bind (UserMeanBaseline, ItemScorer) to GlobalMeanRatingItemScorer
    }

    // and apply some Bayesian damping to the baseline
    within(BaselineScorer, ItemScorer) {
        set MeanDamping to 25.0d
    }
}

target('evaluate') {
    // this requires the ml100k target to be run first
    // can either reference a target by object or by name (as above)
    requires datasets

    trainTest {
        for (d in data) {
            dataset d
        }
        
        // Three different types of output for analysis.
        output "${config.analysisDir}/eval-results.csv"
        predictOutput "${config.analysisDir}/eval-preds.csv"
        userOutput "${config.analysisDir}/eval-user.csv"

        metric CoveragePredictMetric
        metric RMSEPredictMetric
        metric NDCGPredictMetric

        algorithm itemitem
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