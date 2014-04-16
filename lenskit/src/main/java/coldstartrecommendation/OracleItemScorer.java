package coldstartrecommendation;

import it.unimi.dsi.fastutil.longs.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.grouplens.lenskit.RatingPredictor;
import org.grouplens.lenskit.baseline.MeanDamping;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.basic.AbstractRatingPredictor;
import org.grouplens.lenskit.collections.CollectionUtils;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.eval.data.DataSource;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.eval.script.BuiltBy;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * TODO: document me
 */
public class OracleItemScorer extends AbstractItemScorer {

    private final DataSource data;
    private final UserEventDAO ud;
    Long2ObjectOpenHashMap<HashSet<Pair<Long, Double>>> userSets;
    
    @Inject
    OracleItemScorer(DataSource data, UserEventDAO ud) {
        this.data = data;
        this.ud = ud;

        userSets = new Long2ObjectOpenHashMap<HashSet<Pair<Long, Double>>>();
        
        for (Rating r : data.getEventDAO().streamEvents(Rating.class).fast()) {
            long user = r.getUserId();
            if (!userSets.containsKey(user)) {
                userSets.put(user, new HashSet<Pair<Long, Double>>());
            }
            HashSet<Pair<Long, Double>> userSet = userSets.get(user);
            userSet.add(new ImmutablePair<Long, Double>(r.getItemId(), r.getValue()));
        }
    }
    
    
    @Override
    public void score(long user, @Nonnull MutableSparseVector predictions) {
        UserHistory<Rating> ratings = ud.getEventsForUser(user, Rating.class);
        
        HashSet<Pair<Long, Double>> targetSet = new HashSet<Pair<Long, Double>>();
        
        if (ratings != null) {
            for(Rating r : CollectionUtils.fast(ratings)) {
                targetSet.add(new ImmutablePair<Long, Double>(r.getItemId(), r.getValue()));
            }
        }
        LongOpenHashSet neighbors = new LongOpenHashSet();
        for (Map.Entry<Long, HashSet<Pair<Long, Double>>> e : userSets.entrySet()) {
            long neighbor = e.getKey();
            HashSet<Pair<Long, Double>> neighborSet = userSets.get(neighbor);
            if (neighborSet.containsAll(targetSet)) {
                neighbors.add(neighbor);
            }
        }
        
        for (VectorEntry e : CollectionUtils.fast(predictions.fast(VectorEntry.State.EITHER))) {
            long item = e.getKey();
            List<Rating> pastratings = data.getItemEventDAO().getEventsForItem(item, Rating.class);
            if (pastratings == null) {
                continue;
            }
            double d = 0;
            double c = 0;
            for (Rating r : CollectionUtils.fast(pastratings)) {
                if (neighbors.contains(r.getUserId())) {
                    c += 1;
                    d += r.getPreference().getValue();
                }
            }
            if (c>0) {
                predictions.set(e,d/c);
            }
        }
    }
}
