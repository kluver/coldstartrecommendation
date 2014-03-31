package coldstartrecommendation;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.grouplens.lenskit.RatingPredictor;
import org.grouplens.lenskit.baseline.MeanDamping;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.basic.AbstractRatingPredictor;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.eval.data.DataSource;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;

/**
 * TODO: document me
 */
public class OracleItemScorer extends AbstractItemScorer {

    private final DataSource data;
    private final UserEventDAO ud;
    @Inject
    OracleItemScorer(DataSource data, UserEventDAO ud) {
        this.data = data;
        this.ud = ud;
    }
    
    
    @Override
    public void score(long user, @Nonnull MutableSparseVector predictions) {
        UserHistory<Rating> ratings = ud.getEventsForUser(user, Rating.class);
        LongSet neighbors = new LongOpenHashSet(data.getUserDAO().getUserIds());
        if (ratings != null) {
            for(Rating r : ratings) {
                LongOpenHashSet keepers = new LongOpenHashSet();
                for(Rating r2 :data.getItemEventDAO().getEventsForItem(r.getItemId(), Rating.class)) {
                    if (r.getValue() == r2.getValue()) {
                        keepers.add(r2.getUserId());
                    }
                }
                neighbors.retainAll(keepers);
            }
        }
        
        for (VectorEntry e : predictions.fast(VectorEntry.State.EITHER)) {
            long item = e.getKey();
            List<Rating> pastratings = data.getItemEventDAO().getEventsForItem(item, Rating.class);
            if (pastratings == null) {
                continue;
            }
            double d = 0;
            double c = 0;
            for (Rating r : pastratings) {
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
