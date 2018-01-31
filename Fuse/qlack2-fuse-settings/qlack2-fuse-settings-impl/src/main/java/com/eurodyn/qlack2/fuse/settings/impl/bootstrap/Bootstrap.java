package com.eurodyn.qlack2.fuse.settings.impl.bootstrap;

import com.eurodyn.qlack2.util.liquibase.api.LiquibaseBootMigrationsDoneService;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.ops4j.pax.cdi.api.OsgiService;

@Singleton
public class Bootstrap {

  @Inject
  @OsgiService
  /** Make sure liquibase migrations are executed before allowing access
   * to this bundle.
   */
    LiquibaseBootMigrationsDoneService liquibaseBootMigrationsDoneService;

  @PostConstruct
  public void init() {
  }
}
