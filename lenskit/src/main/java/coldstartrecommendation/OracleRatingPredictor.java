package coldstartrecommendation;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.grouplens.lenskit.RatingPredictor;
import org.grouplens.lenskit.basic.AbstractRatingPredictor;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;

/**
 * TODO: document me
 */
public class OracleRatingPredictor extends AbstractRatingPredictor {

    private final UserEventDAO uedao;
    private final ItemEventDAO iedao;
    private final UserDAO userdao;
    
    @Inject
    OracleRatingPredictor(UserEventDAO uedao, ItemEventDAO iedao, UserDAO userdao) {
        this.uedao = uedao;
        this.iedao = iedao;
        this.userdao = userdao;
    }
    
    
    @Override
    public void predict(long user, @Nonnull MutableSparseVector predictions) {
        // generate list of "neighbors"
        UserHistory<Rating> ratings = uedao.getEventsForUser(user, Rating.class);
        LongSet neighbors = new LongOpenHashSet(userdao.getUserIds());
        if (ratings != null) {
            for (long itemId : ratings.itemSet()) {
                neighbors.retainAll(iedao.getUsersForItem(itemId));
            }
        }
        
        for (VectorEntry e : predictions.fast(VectorEntry.State.EITHER)) {
            long item = e.getKey();
            List<Rating> pastratings = iedao.getEventsForItem(item, Rating.class);
            if (pastratings == null) {
                continue;
            }
            double d = 0;
            int c = 0;
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
