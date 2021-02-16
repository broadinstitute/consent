package org.broadinstitute.consent.http.db;

import org.broadinstitute.consent.http.db.mapper.MatchMapper;
import org.broadinstitute.consent.http.models.Match;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@RegisterRowMapper(MatchMapper.class)
public interface MatchDAO extends Transactional<MatchDAO> {

    @SqlQuery("select * from match_entity where consent = :consentId ")
    List<Match> findMatchByConsentId(@Bind("consentId") String consentId);

    @SqlQuery("select * from match_entity where purpose = :purposeId ")
    List<Match>  findMatchByPurposeId(@Bind("purposeId") String purposeId);

    @SqlQuery("select * from match_entity where purpose = :purposeId and consent = :consentId ")
    Match  findMatchByPurposeIdAndConsent(@Bind("purposeId") String purposeId, @Bind("consentId") String consentId);

    @SqlQuery("select * from match_entity where matchId = :id ")
    Match  findMatchById(@Bind("id") Integer id);

    @SqlUpdate("insert into match_entity " +
            "(consent, purpose, matchEntity, failed, date) values " +
            "(:consentId, :purposeId, :match, :failed, :createDate)")
    @GetGeneratedKeys
    Integer insertMatch(@Bind("consentId") String consentId,
                        @Bind("purposeId") String purposeId,
                        @Bind("match") Boolean match,
                        @Bind("failed") Boolean failed,
                        @Bind("createDate") Date date);

    @SqlBatch("insert into match_entity (consent, purpose, matchEntity, failed, createDate) values (:consent, :purpose, :match, :failed, :createDate)")
    void insertAll(@BindBean List<Match> matches);

    @SqlUpdate("update match_entity set matchEntity = :match, consent = :consentId, purpose = :purposeId, failed = :failed where matchId = :id ")
    void updateMatch(@BindList("match") Boolean match,
                     @Bind("consentId") String consent,
                     @Bind("purposeId") String purpose,
                     @Bind("failed") Boolean failed);

    @SqlBatch("DELETE FROM match_entity WHERE matchid IN (<matchIds>)")
    void deleteMatches(@BindList("matchIds") Collection<Integer> matchIds);

    @SqlUpdate("DELETE FROM match_entity WHERE consent = :consentId")
    void deleteMatchByConsentId(@Bind("consentId") String consentId);

    @SqlQuery("SELECT COUNT(*) FROM match_entity where matchEntity = :matchEntity and failed ='FALSE' ")
    Integer countMatchesByResult(@Bind("matchEntity") Boolean matchEntity);
}
