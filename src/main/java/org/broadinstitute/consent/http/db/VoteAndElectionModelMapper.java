package org.broadinstitute.consent.http.db;

import org.broadinstitute.consent.http.mail.freemarker.VoteAndElectionModel;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;


public class VoteAndElectionModelMapper implements RowMapper<VoteAndElectionModel> {
    @Override
    public VoteAndElectionModel map(ResultSet r, StatementContext statementContext) throws SQLException {
        return new VoteAndElectionModel(Integer.toString(r.getInt("electionId")), r.getString("referenceId"), r.getString("electionType"), r.getString("type"));
    }
}
