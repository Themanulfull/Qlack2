package com.eurodyn.qlack2.util.liquibase.impl;

import com.eurodyn.qlack2.util.liquibase.api.QChangeLogEntry;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.osgi.OSGiResourceAccessor;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a changelog and updates the underlying database.
 */
public class QChangeLogRunner {

  /**
   * JUL reference
   */
  private final static Logger LOGGER = Logger.getLogger(QChangeLogRunner.class.getName());

  // Apply Liquibase changelog. Returns true if no Liquibase exception occurred.
  private static boolean runMigration(QChangeLogEntry changeLogEntry, Connection connection) {
    LOGGER.log(Level.INFO, "Executing: Priority {0}, ChangeLog: {1}.",
      new Object[]{changeLogEntry.getPriority(), changeLogEntry.getChangeLog()});
    try {
      /** Get a connection and setup specific database vendor handler */
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
        new JdbcConnection(connection));

      /** Initialise changelog history table */
      if (!MigrationExecutor.isChangeLogHistoryInitialised()) {
        ChangeLogHistoryServiceFactory.getInstance().getChangeLogService(database).init();
        MigrationExecutor.setChangeLogHistoryInitialised(true);
      }

      /** Setup changelog parameters */
      ChangeLogParameters changeLogParameters = new ChangeLogParameters();
      for (String s : changeLogEntry.getProperties().keySet()) {
        changeLogParameters.set(s, changeLogEntry.getProperty(s));
      }

      /** Setup the XLM parser for this changelog */
      ChangeLogParser changeLogParser = new XMLChangeLogSAXParser();
      final DatabaseChangeLog dbChangeLog = changeLogParser.parse(changeLogEntry.getChangeLog(),
        changeLogParameters,
        new OSGiResourceAccessor(changeLogEntry.getBundle()));

      /** Update (no context support) */
      Liquibase liquibase = new Liquibase(dbChangeLog, new OSGiResourceAccessor(
        changeLogEntry.getBundle()), database);
      liquibase.update("");
      return true;
    } catch (LiquibaseException e) {
      LOGGER.log(Level.SEVERE, MessageFormat.format(
        "Could not execute changelog {0}.", changeLogEntry.getChangeLog()), e);
      return false;
    }
  }

  public static boolean runMigrations() {
    boolean migrationsSucceeded = true;
    while (MigrationExecutor.queue.size() > 0) {
      /** Try to get a connection to test if the Datasource is alive. If not, wait and re-try.
       * The Datasource will be automatically refreshed by migrationMonitoringDSThread. */
      DataSource dataSource = null;
      boolean isDSActive = false;
      Connection connection = null;
      while (!isDSActive) {
        try {
          dataSource = MigrationExecutor.getDataSource();
          connection = dataSource.getConnection();
          isDSActive = true;
        } catch (SQLException e) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e1) {
          }
        }
      }

      try {
        final QChangeLogEntry qChangeLogEntry = MigrationExecutor.queue.remove();
        migrationsSucceeded &= QChangeLogRunner.runMigration(qChangeLogEntry, connection);
      } finally {
        try {
          if (connection != null && !connection.isClosed()) {
            connection.close();
          }
        } catch (SQLException e) {
          LOGGER.log(Level.WARNING, "Could not close database connection: {0}.", connection);
        }
      }
    }
    return migrationsSucceeded;
  }
}
