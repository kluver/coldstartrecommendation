package coldstartrecommendation;


import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.io.Closer;
import it.unimi.dsi.fastutil.longs.*;
import org.grouplens.lenskit.collections.CollectionUtils;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.cursors.Cursors;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.UserDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.data.pref.Preference;
import org.grouplens.lenskit.eval.AbstractTask;
import org.grouplens.lenskit.eval.TaskExecutionException;
import org.grouplens.lenskit.eval.data.CSVDataSourceBuilder;
import org.grouplens.lenskit.eval.data.DataSource;
import org.grouplens.lenskit.eval.data.crossfold.*;
import org.grouplens.lenskit.eval.data.traintest.GenericTTDataBuilder;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.util.io.Describer;
import org.grouplens.lenskit.util.io.Descriptions;
import org.grouplens.lenskit.util.io.HashDescriptionWriter;
import org.grouplens.lenskit.util.io.UpToDateChecker;
import org.grouplens.lenskit.util.table.writer.CSVWriter;
import org.grouplens.lenskit.util.table.writer.TableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * A task that takes a crossfold and downsamples each user in each training set.
 * For now this only supports downsampling to a constant size, in the future, 
 * it should support arbitrary downsamplers.
 */
public class DownsampleDataSet extends AbstractTask<List<TTDataSet>> {
    private static final Logger logger = LoggerFactory.getLogger(DownsampleDataSet.class);

    List<TTDataSet> sources;
    private int retain = 0;
    private String directory = "";
    
    public DownsampleDataSet() {
        super(null);
    }

    public DownsampleDataSet(String n) {
        super(n);
    }

    public int getRetain() {
        return retain;
    }

    public DownsampleDataSet setRetain(int retain) {
        this.retain = retain;
        return  this;
    }

    public List<TTDataSet> getSources() {
        return sources;
    }

    public DownsampleDataSet setSources(List<TTDataSet> sources) {
        this.sources = sources;
        return this;
    }

    public String getDirectory() {
        return directory;
    }

    public DownsampleDataSet setDirectory(String directory) {
        this.directory = directory;
        return this;
    }

    @Override
    protected List<TTDataSet> perform() throws TaskExecutionException, InterruptedException {
        Preconditions.checkNotNull(sources);
        List<TTDataSet> datasets = new ArrayList<TTDataSet>(sources.size());
        for (TTDataSet dataset: sources) {
            try {
                GenericTTDataBuilder builder = new GenericTTDataBuilder();
                if (getName() == null) {
                    builder.setName(dataset.getName());
                } else {
                    builder.setName(getName());
                }
                for (Map.Entry<String, Object> entry : dataset.getAttributes().entrySet()) {
                    builder.setAttribute(entry.getKey(), entry.getValue());
                }
                builder.setAttribute("Retain", retain);
                builder.setQuery(dataset.getQueryData());
                builder.setTest(dataset.getTestData());
                builder.setTrain(downsample(dataset.getTrainingData(), dataset.getTestData().getUserDAO().getUserIds()));
                datasets.add(builder.build());
            } catch (IOException e) {
                throw new TaskExecutionException(e);
            }
        }
        return datasets;
    }

    private DataSource downsample(DataSource data, LongSet testUsers) throws IOException {
        String fileName = getFileName(data);
        File output = new File(fileName);
        UpToDateChecker checker = new UpToDateChecker();
        
        checker.addInput(data.lastModified());
        checker.addOutput(output);
        if (!checker.isUpToDate()) {
            RandomOrder<Rating> order = new RandomOrder<Rating>();
            Random rng = new Random();
            // write datasource
            CSVWriter csv = null;
            try {
                
                csv = CSVWriter.open(output, null);
                Cursor<UserHistory<Rating>> histories = data.getUserEventDAO().streamEventsByUser(Rating.class);
                for (UserHistory<Rating> ratings : histories) {
                    List<Rating> rats = new ArrayList<Rating>(ratings);
                    order.apply(rats, rng);
                    for (int i = 0; i < rats.size(); i++) {
                        if (!testUsers.contains(ratings.getUserId()) || i < retain) {
                            Rating rating = rats.get(i);
                            Preference pref = rating.getPreference();
                            csv.writeRow(Lists.newArrayList(rating.getUserId(), rating.getItemId(), rating.getValue(), rating.getTimestamp()));
                        }
                    }
                }
            } finally {
                if (csv != null) {
                    csv.close();
                }
            }
        }
        
        CSVDataSourceBuilder builder = new CSVDataSourceBuilder(data.getName());
        builder.setDomain(data.getPreferenceDomain());
        builder.setFile(output);
        return builder.build();
    }

    private String getFileName(DataSource data) {
        // our dataSources don't normally have descriptions, but our eventDaos do.
        // in the normal use case of this the eventDao is perfect.
        EventDAO eventDAO = data.getEventDAO();

        HashDescriptionWriter writer = Descriptions.sha1Writer();
        Descriptions.defaultDescriber().describe(eventDAO, writer);
        String name = writer.finish().toString();
        
        return directory + "/" + name + "-"+retain+".csv";
    }
}
