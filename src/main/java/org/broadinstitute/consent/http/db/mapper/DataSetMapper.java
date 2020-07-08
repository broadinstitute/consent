package org.broadinstitute.consent.http.db.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.broadinstitute.consent.http.models.DataSet;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class DataSetMapper implements RowMapper<DataSet> {

  public DataSet map(ResultSet r, StatementContext ctx) throws SQLException {
    return new DataSet(
        r.getInt("dataSetId"),
        r.getString("objectId"),
        r.getString("name"),
        r.getTimestamp("createDate"),
        r.getBoolean("active"),
        r.getInt("alias"));
  }
}