package coldstartrecommendation;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.baseline.BaselineScorer;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.symbols.Symbol;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class BaselineMixingItemScorer extends AbstractItemScorer {
    private final ItemScorer baseline;
    private final ItemScorer wrapped;
    private final Symbol weightSym;
    private final double mixingWeight;
    
    @Inject
    public BaselineMixingItemScorer (@BaselineScorer ItemScorer baseline, 
                                     ItemScorer wrapped, 
                                     @WeightSymbol Symbol weightSym, 
                                     @MixingWeight double mixingWeight) {
        this.baseline = baseline;
        this.wrapped = wrapped;
        this.weightSym = weightSym;
        this.mixingWeight = mixingWeight;
        
    }
    
            
    @Override
    public void score(long user, @Nonnull MutableSparseVector vectorEntries) {
        MutableSparseVector baselineCopy = vectorEntries.mutableCopy();
        baseline.score(user, baselineCopy);
        
        wrapped.score(user, vectorEntries);
        MutableSparseVector weights = vectorEntries.getOrAddChannelVector(weightSym);
        
        // compute inverse for re-scaling the scores
        for (VectorEntry e : vectorEntries.fast(VectorEntry.State.EITHER)) {
            if (vectorEntries.isSet(e) && weights.isSet(e)) {
                double score = vectorEntries.get(e);
                double weight = weights.get(e);
                double baselineValue = baselineCopy.get(e);
                vectorEntries.set(e, ((score*weight+baselineValue*mixingWeight)/(weight+mixingWeight)));
            } else {
                vectorEntries.set(e,baselineCopy.get(e));
            }
        }        
    }
}
