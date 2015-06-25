package org.genomebridge.consent.http.db;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.genomebridge.consent.http.models.Vote;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class VoteMapper implements ResultSetMapper<Vote> {
	
    public Vote map(int index, ResultSet r, StatementContext ctx) throws SQLException  {
       	 return new Vote(
                 r.getInt("voteId"),
                 (r.getString("vote")==null) ? null : r.getBoolean("vote"),
                 r.getInt("dacUserId"),
                 r.getDate("createDate"),
                 r.getDate("updateDate"),
                 r.getInt("electionId"),
                 r.getString("rationale")
         );
       
    }
}
