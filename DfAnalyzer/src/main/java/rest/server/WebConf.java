package rest.server;

import di.dataflow.object.ProcessObject;
import di.enumeration.dbms.DBMS;
import di.enumeration.process.ProcessType;
import di.object.process.DaemonDI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sql2o.Sql2o;
import pde.PDE;
import process.background.ProvenanceQueue;
import rest.config.DbConfiguration;
import rest.config.DbConnection;
import utils.Utils;

/**
 * @author Thaylon Guedes, Vítor Silva
 * @email thaylongs@gmail.com, vitor.silva.sousa@gmail.com
 */
@Configuration
public class WebConf {

    DbConnection db;
    ProvenanceQueue queue;

    @Bean
    DbConnection dbConnection() {
        if (db == null) {
            di.main.EnvironmentConfiguration config = di.main.Utils.readConfigurationFile();
            DbConfiguration dbConfig = new DbConfiguration(config.getPort(),
                    config.getServer(),
                    config.getDatabaseName(),
                    config.getUser(),
                    config.getPassword(),
                    config.getDBMS(),
                    config.getDataIngestorDirectory());
            this.db = new DbConnection(dbConfig);
        }
        return this.db;
    }

    @Bean
    PDE provenance() {
        if (db == null) {
            dbConnection();
        }
        return new PDE(db.config.getDataIngestorDirectory());
    }

    /** Bean próprio para os contadores da fila serem lidos pela API. */
    @Bean
    ProvenanceQueue provenanceQueue() {
        if (db == null) {
            dbConnection();
        }
        if (queue == null) {
            queue = new ProvenanceQueue(this.db);
            queue.start();
        }
        return queue;
    }

    @Bean
    DaemonDI database() {
        if (db == null) {
            dbConnection();
        }
        ProvenanceQueue queue = provenanceQueue();
        ProcessObject obj = ProcessObject.newInstance(
                this.db.config.getDataIngestorDirectory(), ProcessType.DAEMON);
        DaemonDI daemon = (DaemonDI) obj;
        daemon.queue = queue;
        daemon.dbms = DBMS.MONETDB;
        daemon.start();
        return daemon;
    }

    @Bean
    Sql2o sql2o() {
        try {
            Class.forName("nl.cwi.monetdb.jdbc.MonetDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        DbConfiguration config = Utils.readConfigurationFile();
        return new Sql2o("jdbc:monetdb://" + config.getServer() + ":" + config.getPort()
                + "/" + config.getDatabaseName(), config.getUser(), config.getPassword());
    }
}
