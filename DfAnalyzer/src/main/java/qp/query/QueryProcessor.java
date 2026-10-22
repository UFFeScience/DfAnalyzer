package qp.query;

import rest.config.DbConnection;
import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import static java.util.stream.Collectors.toCollection;
import pde.enumeration.ExtractionCartridge;
import qp.tracing.Path;
import qp.tracing.PathFinder;

/**
 *
 * @author tperrotta, vitorss
 */
public class QueryProcessor {

    private final DbConnection dbConnection;
    private final Dataflow dataflow;
    private final PathFinder pathfinder;

    public QueryProcessor(DbConnection dbConnection, Dataflow dataflow) {
        this.dbConnection = dbConnection;
        this.dataflow = dataflow;
        this.pathfinder = new PathFinder(dataflow);
    }

    public Dataflow getDataflow() {
        return this.dataflow;
    }

    /**
     * Algorithm 6
     * @return the SQL query satisfying the specified conditions and filters
     */
    public MonetDbSqlQuery runSqlQuery(QuerySpecification spec) {
        return runSqlQuery(spec, true);
    }

    public MonetDbSqlQuery runSqlQuery(QuerySpecification spec, boolean writeCsv) {
        SqlClause selectClause = new SqlClause(null, ", ");
        SqlClause whereClause = new SqlClause(null, " AND ");

        Set<Dataset> fromDatasets = new LinkedHashSet<>();
        SqlClause fromClause = new SqlClause(null, ", ");

        // User-provided projections
        for (String projection : spec.getProjections()) {
            selectClause.addElement(projection);
        }

        // User-provided selections
        for (String selection : spec.getSelections()) {
            whereClause.addElement(new Expression(selection).toString());
        }

        // Cartesian product: Inputs x Outputs
        for (String dsOriginStr : spec.getSources()) {
            for (String dsDestinationStr : spec.getTargets()) {
                Collection<Path> paths = this.pathfinder.findPaths(dsOriginStr, dsDestinationStr, spec.getIncludes(), spec.getExcludes());

                if (paths == null && spec.getSources().size() == 1) {
                    return createQueryToASingleTable(dsOriginStr, spec.getProjections(), spec.getSelections());
                } else {
                    handlePaths(paths, fromDatasets,
                            spec.getMapping(), spec.getProjections(),
                            selectClause, fromClause, whereClause);
                }
            }
        }
        
        //change here
        fromClause.addElements(fromDatasets.stream().map(ds -> ds.getTag()).collect(toCollection(LinkedHashSet::new)));

        MonetDbSqlQuery query = new MonetDbSqlQuery(selectClause,fromClause,whereClause);
        if (writeCsv) {
            dbConnection.runMonetDBQuery(query);
        }
        
        return query;
    }

    public void handlePaths(Collection<Path> paths,
            Set<Dataset> fromDatasets,
            AttrMapping.Type attrMappingType,
            Collection<String> projections,
            SqlClause selectClause,
            SqlClause fromClause,
            SqlClause whereClause) {
        // From Datasets
        paths.forEach(path -> path.forEach(ds -> fromDatasets.add(ds)));

        // Attribute Mappings
        for (Path path : paths) {
            for (int i = 1; i < path.size(); ++i) {
                Dataset prevDataset = path.get(i - 1);
                Dataset nextDataset = path.get(i);

                AttrMapping attrMapping = new AttrMapping(prevDataset, nextDataset, attrMappingType, dataflow.getTransformation(prevDataset, nextDataset).get());
                Collection<String> elements = attrMapping.toSqlClauseElements();

                // Attribute mappings selections (internal)
                for (Iterator<String> it = elements.iterator(); it.hasNext();) {
                    String leftElement = it.next();
                    String rightElement = it.next();

                    whereClause.addElement(new Expression(leftElement + " = " + rightElement).toString());
                }

                // user didn't specify any projections; therefore, calculate all possible projections (grab every attribute pair)
                if (projections.isEmpty()) {
                    selectClause.addElements(elements);
                }
            }
        }
    }

    private MonetDbSqlQuery createQueryToASingleTable(String dsOriginStr, Collection<String> projections, Collection<String> selections) {
        //create a query to a single table (or dataset)
        Optional<Dataset> optDs = dataflow.getDataset(dsOriginStr);
        MonetDbSqlQuery query = null;
        if (optDs.isPresent()) {
            Dataset dataset = optDs.get();
            //evaluate if we have an indexing extractor
            if (dataset.hasIndexingExtractor()) {
                query = generateQueryWithIndexingExtractors(dataset, projections, selections);
                dbConnection.runQueries(dataset, query);
            } else {
                //without extractors with indexing method, we only need to analyze the dataset in MonetDB's database
                query = generateQueryWithoutIndexingExtractors(dataset, projections, selections);
            }
        }
        return query;
    }

    private MonetDbSqlQuery generateQueryWithoutIndexingExtractors(Dataset dataset, Collection<String> projections, Collection<String> selections) {
        SqlClause selectClause = new SqlClause(null, ", ");
        SqlClause whereClause = new SqlClause(null, " AND ");
        SqlClause fromClause = new SqlClause(null, ", ");
        //a single table
        fromClause.addElement(dataset.getTag());
        //projections
        projections.forEach((projection) -> {
            selectClause.addElement(projection);
        });
        //selections
        selections.forEach((selection) -> {
            whereClause.addElement(selection);
        });
        return new MonetDbSqlQuery(selectClause, fromClause, whereClause);
    }

    private MonetDbSqlQuery generateQueryWithIndexingExtractors(Dataset dataset, Collection<String> projections, Collection<String> selections) {
        MonetDbSqlQuery mdbQuery = new MonetDbSqlQuery();
        //monetdb clauses
        SqlClause mdbSelectClause = new SqlClause(null, ", ");
        SqlClause mdbFromClause = new SqlClause(null, ", ");
        SqlClause mdbWhereClause = new SqlClause(null, " AND ");
        //dataset
        mdbFromClause.addElement(dataset.getTag());
        //clone
        Collection<String> cloneProjs = new ArrayList();
        Collection<String> cloneSelecs = new ArrayList();
        //only add relevant projections and selections
        projections.stream().filter((proj) -> (proj.split("\\.")[0].equals(dataset.getTag()))).forEachOrdered((proj) -> {
            cloneProjs.add(proj);
        });
        selections.stream().filter((select) -> (select.split("\\.")[0].equals(dataset.getTag()))).forEachOrdered((select) -> {
            cloneSelecs.add(select);
        });
        //indexing extractors
        for (Extractor ext : dataset.getIndexingExtractors()) {
            //clauses to indexer query
            SqlClause idxSelectClause = new SqlClause(null, ", ");
            SqlClause idxFromClause = new SqlClause(null, ", ");
            SqlClause idxWhereClause = new SqlClause(null, " AND ");
            //projection
            for (String projection : projections) {
                String[] split = projection.split("\\.");
                Optional<Attribute> att = dataset.getAttribute(split[1]);
                if (split[0].equals(dataset.getTag()) && att.isPresent()
                        && att.get().getExtractor().tag.equals(ext.getTag())) {
                    cloneProjs.remove(projection);
                    idxSelectClause.addElement(split[1]);
                }
            }
            //selection
            for (String selection : selections) {
                String delimiters = "[=><]";
                String[] split = selection.split(delimiters);
                split = split[0].trim().split("\\.");
                Optional<Attribute> att = dataset.getAttribute(split[1]);
                if (split[0].equals(dataset.getTag()) && att.isPresent()
                        && att.get().getExtractor().tag.equals(ext.getTag())) {
                    cloneSelecs.remove(selection);
                    idxWhereClause.addElement(selection.replace(dataset.getTag() + ".", ""));
                }
            }
            //index path
            if (ext.cartridge == ExtractionCartridge.OPTIMIZED_FASTBIT
                    && (!idxSelectClause.getElements().isEmpty()
                    || !idxWhereClause.getElements().isEmpty())) {
                mdbSelectClause.addElement(dataset.getTag() + "_filename");
            }

            FastBitQuery fbQuery = new FastBitQuery(idxSelectClause, idxFromClause, idxWhereClause);
            mdbQuery.addIndexerQuery(ext, fbQuery);
            mdbQuery.addIndexerAttribute(dataset.getTag() + "_filename", ext);
        }
        //add attributes that do not apply an indexing method
        for (String proj : cloneProjs) {
            mdbSelectClause.addElement(proj);
        }
        //monetdb query
        mdbQuery.setSelectClause(mdbSelectClause);
        mdbQuery.setFromClause(mdbFromClause);
        mdbQuery.setWhereClause(mdbWhereClause);
        return mdbQuery;
    }
}
