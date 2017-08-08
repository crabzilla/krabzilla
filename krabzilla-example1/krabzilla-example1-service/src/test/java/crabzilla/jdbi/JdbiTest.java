package crabzilla.jdbi;

import org.jdbi.v3.core.Jdbi;
import org.junit.Ignore;
import org.junit.Test;

public class JdbiTest {

  Jdbi jdbi ;

  @Test @Ignore
  public void t1() {
    String woot = jdbi.open().inTransaction(x -> "Woot!");
  }

}
