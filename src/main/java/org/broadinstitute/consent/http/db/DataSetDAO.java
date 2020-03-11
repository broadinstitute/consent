package org.broadinstitute.consent.http.db;

import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.consent.http.models.Association;
import org.broadinstitute.consent.http.models.Dac;
import org.broadinstitute.consent.http.models.DataSet;
import org.broadinstitute.consent.http.models.DataSetProperty;
import org.broadinstitute.consent.http.models.Dictionary;
import org.broadinstitute.consent.http.models.dto.DataSetDTO;
import org.broadinstitute.consent.http.resources.Resource;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterRowMapper(DataSetMapper.class)
public interface DataSetDAO extends Transactional<DataSetDAO> {

    String CHAIRPERSON = Resource.CHAIRPERSON;

    @SqlUpdate("insert into dataset (name, createDate, objectId, active, alias) values (:name, :createDate, :objectId, :active, :alias)")
    @GetGeneratedKeys
    Integer insertDataset(@Bind("name") String name, @Bind("createDate") Date createDate, @Bind("objectId") String objectId, @Bind("active") Boolean active, @Bind("alias") Integer alias);

    @SqlQuery("select * from dataset where dataSetId = :dataSetId")
    DataSet findDataSetById(@Bind("dataSetId") Integer dataSetId);

    @SqlQuery("select * from dataset where dataSetId in (<dataSetIdList>)")
    List<DataSet> findDataSetsByIdList(@BindList("dataSetIdList") List<Integer> dataSetIdList);

    @SqlQuery("select * from dataset where objectId = :objectId")
    DataSet findDataSetByObjectId(@Bind("objectId") String objectId);

    @SqlQuery("select objectId from dataset where dataSetId = :dataSetId")
    String findObjectIdByDataSetId(@Bind("dataSetId") Integer dataSetId);

    @SqlQuery("select * from dataset where objectId = :objectId")
    Integer findDataSetIdByObjectId(@Bind("objectId") String objectId);

    @SqlQuery("select * from dataset where dataSetId in (<dataSetIdList>) and needs_approval = true")
    List<DataSet> findNeedsApprovalDataSetByDataSetId(@BindList("dataSetIdList") List<Integer> dataSetIdList);

    @SqlBatch("insert into dataset (name, createDate, objectId, active, alias) values (:name, :createDate, :objectId, :active, :alias)")
    void insertAll(@BindBean Collection<DataSet> dataSets);

    @SqlBatch("update dataset set name = :name, createDate = :createDate, active = :active, alias = :alias where dataSetId = :dataSetId")
    void updateAll(@BindBean Collection<DataSet> dataSets);

    @SqlBatch("update dataset set name = :name, active = :active, createDate = :createDate, alias = :alias where objectId = :objectId")
    void updateAllByObjectId(@BindBean Collection<DataSet> dataSets);

    @SqlBatch("insert into datasetproperty (dataSetId, propertyKey, propertyValue, createDate )" +
            " values (:dataSetId, :propertyKey, :propertyValue, :createDate)")
    void insertDataSetsProperties(@BindBean List<DataSetProperty> dataSetPropertiesList);

    @SqlBatch("delete from datasetproperty where dataSetId = :dataSetId")
    void deleteDataSetsProperties(@Bind("dataSetId") Collection<Integer> dataSetsIds);

    @SqlBatch("delete from dataset where dataSetId = :dataSetId")
    void deleteDataSets(@Bind("dataSetId") Collection<Integer> dataSetsIds);

    @SqlUpdate("update dataset set active = null, name = null, createDate = null, needs_approval = 0 where dataSetId = :dataSetId")
    void logicalDataSetdelete(@Bind("dataSetId") Integer dataSetId);

    @SqlUpdate("update dataset set active = :active where dataSetId = :dataSetId")
    void updateDataSetActive(@Bind("dataSetId") Integer dataSetId, @Bind("active") Boolean active);

    @SqlUpdate("update dataset set needs_approval = :needs_approval where dataSetId = :dataSetId")
    void updateDataSetNeedsApproval(@Bind("dataSetId") Integer dataSetId, @Bind("needs_approval") Boolean needs_approval);

    @UseRowMapper(DataSetPropertiesMapper.class)
    @SqlQuery("select d.*, k.key, dp.propertyValue, ca.consentId , c.translatedUseRestriction " +
            "from dataset d inner join datasetproperty dp on dp.dataSetId = d.dataSetId and d.name is not null inner join dictionary k on k.keyId = dp.propertyKey " +
            "inner join consentassociations ca on ca.dataSetId = d.dataSetId inner join consents c on c.consentId = ca.consentId " +
            "order by d.dataSetId, k.displayOrder")
    Set<DataSetDTO> findDataSets();

    @UseRowMapper(DataSetPropertiesMapper.class)
    @SqlQuery("select d.*, k.key, dp.propertyValue, ca.consentId , c.translatedUseRestriction " +
            "from dataset d inner join datasetproperty dp on dp.dataSetId = d.dataSetId inner join dictionary k on k.keyId = dp.propertyKey " +
            "inner join consentassociations ca on ca.dataSetId = d.dataSetId inner join consents c on c.consentId = ca.consentId " +
            "where d.dataSetId = :dataSetId order by d.dataSetId, k.displayOrder")
    Set<DataSetDTO> findDataSetWithPropertiesByDataSetId(@Bind("dataSetId") Integer dataSetId);

    @UseRowMapper(DataSetPropertiesMapper.class)
    @SqlQuery(" select d.*, k.key, dp.propertyValue, ca.consentId , c.translatedUseRestriction from dataset  d inner join datasetproperty dp on dp.dataSetId = d.dataSetId " +
            " inner join dictionary k on k.keyId = dp.propertyKey inner join consentassociations ca on ca.dataSetId = d.dataSetId inner join consents c on c.consentId = ca.consentId inner join election e on e.referenceId = ca.consentId " +
            " inner join vote v on v.electionId = e.electionId and v.type = '" + CHAIRPERSON  + "' inner join (SELECT referenceId,MAX(createDate) maxDate FROM election where status ='Closed' group by referenceId) ev on ev.maxDate = e.createDate " +
            " and ev.referenceId = e.referenceId and v.vote = true and d.active = true order by d.dataSetId, k.displayOrder")
    Set<DataSetDTO> findDataSetsForResearcher();

    @UseRowMapper(DataSetPropertiesMapper.class)
    @SqlQuery("select d.*, k.key, dp.propertyValue, ca.consentId , c.translatedUseRestriction " +
            "from dataset d inner join datasetproperty dp on dp.dataSetId = d.dataSetId inner join dictionary k on k.keyId = dp.propertyKey " +
            "inner join consentassociations ca on ca.dataSetId = d.dataSetId inner join consents c on c.consentId = ca.consentId " +
            "where d.dataSetId in (<dataSetIdList>) order by d.dataSetId, k.receiveOrder")
    Set<DataSetDTO> findDataSetsByReceiveOrder(@BindList("dataSetIdList") List<Integer> dataSetIdList);


    @SqlQuery("select *  from dataset where objectId in (<objectIdList>) ")
    List<DataSet> searchDataSetsByObjectIdList(@BindList("objectIdList") List<String> objectIdList);

    @SqlQuery("select ds.dataSetId  from dataset ds where ds.objectId in (<objectIdList>) ")
    List<Integer> searchDataSetsIdsByObjectIdList(@BindList("objectIdList") List<String> objectIdList);

    @RegisterRowMapper(DictionaryMapper.class)
    @SqlQuery("SELECT * FROM dictionary d order by receiveOrder")
    List<Dictionary> getMappedFieldsOrderByReceiveOrder();

    @RegisterRowMapper(DictionaryMapper.class)
    @SqlQuery("SELECT * FROM dictionary d WHERE d.displayOrder is not null  order by displayOrder")
    List<Dictionary> getMappedFieldsOrderByDisplayOrder();

    @SqlQuery("SELECT * FROM dataset d WHERE d.objectId IN (<objectIdList>)")
    List<DataSet> getDataSetsForObjectIdList(@BindList("objectIdList") List<String> objectIdList);

    @SqlQuery("SELECT * FROM dataset d WHERE d.name IN (<names>)")
    List<DataSet> getDataSetsForNameList(@BindList("names") List<String> names);

    @SqlQuery("SELECT ds.* FROM consentassociations ca inner join dataset ds on ds.dataSetId = ca.dataSetId WHERE ca.consentId = :consentId")
    List<DataSet> getDataSetsForConsent(@Bind("consentId") String consentId);

    @RegisterRowMapper(AssociationMapper.class)
    @SqlQuery("SELECT * FROM consentassociations ca inner join dataset ds on ds.dataSetId = ca.dataSetId WHERE ds.objectId IN (<objectIdList>)")
    List<Association> getAssociationsForObjectIdList(@BindList("objectIdList") List<String> objectIdList);

    @RegisterRowMapper(AssociationMapper.class)
    @SqlQuery("SELECT * FROM consentassociations ca inner join dataset ds on ds.dataSetId = ca.dataSetId WHERE ds.dataSetId IN (<dataSetIdList>)")
    List<Association> getAssociationsForDataSetIdList(@BindList("dataSetIdList") List<Integer> dataSetIdList);

    @RegisterRowMapper(AutocompleteMapper.class)
    @SqlQuery("SELECT DISTINCT d.dataSetId as id, d.objectId as objId, CONCAT_WS(' | ', d.objectId, d.name, dsp.propertyValue, c.name) as concatenation FROM dataset d " +
            " inner join consentassociations ca on ca.dataSetId = d.dataSetId and d.active = true" +
            " inner join consents c on c.consentId = ca.consentId " +
            " inner join election e on e.referenceId = ca.consentId " +
            " inner join datasetproperty dsp on dsp.dataSetId = d.dataSetId and dsp.propertyKey IN (9) " +
            " inner join vote v on v.electionId = e.electionId and v.type = '" + CHAIRPERSON  +
            "'inner join (SELECT referenceId,MAX(createDate) maxDate FROM" +
            " election where status ='Closed' group by referenceId) ev on ev.maxDate = e.createDate and ev.referenceId = e.referenceId " +
            " and v.vote = true " +
            " and (d.objectId like concat('%',:partial,'%') " +
            " or d.name like concat('%',:partial,'%') " +
            " or dsp.propertyValue like concat('%',:partial,'%')" +
            " or c.name like concat('%',:partial,'%') )" +
            " order by d.dataSetId")
    List< Map<String, String>> getObjectIdsbyPartial(@Bind("partial") String partial);

    @RegisterRowMapper(AutocompleteMapper.class)
    @SqlQuery("SELECT DISTINCT d.dataSetId as id, d.objectId as objId, CONCAT_WS(' | ', d.objectId, d.name, dsp.propertyValue, c.name) as concatenation " +
            "FROM dataset d inner join datasetproperty dsp on dsp.dataSetId = d.dataSetId and dsp.propertyKey IN (9) " +
            "inner join consentassociations ca on ca.dataSetId = d.dataSetId and d.active = true " +
            "inner join consents c on c.consentId = ca.consentId " +
            "WHERE d.name = :name")
    List< Map<String, String>> getObjectIdsbyDataSetName(@Bind("name")String name);

    @SqlQuery("SELECT ca.associationId FROM consentassociations ca INNER JOIN dataset ds on ds.dataSetId = ca.dataSetId WHERE ds.objectId = :objectId")
    Integer getConsentAssociationByObjectId(@Bind("objectId") String objectId);

    @SqlQuery("SELECT ca.consentId FROM consentassociations ca INNER JOIN dataset ds on ds.dataSetId = ca.dataSetId WHERE ds.dataSetId = :dataSetId")
    String getAssociatedConsentIdByDataSetId(@Bind("dataSetId") Integer dataSetId);

    @SqlQuery("SELECT dataSetId FROM dataset WHERE name = :name")
    Integer getDataSetByName(@Bind("name") String name);

    @SqlQuery("select *  from dataset where name in (<names>) ")
    List<DataSet> searchDataSetsByNameList(@BindList("names") List<String> names);

    @UseRowMapper(BatchMapper.class)
    @SqlQuery("select dataSetId, name  from dataset where name in (<nameList>)")
    List<Map<String,Integer>> searchByNameIdList(@BindList("nameList") List<String> nameList);

    @SqlQuery(" SELECT * FROM dataset d WHERE d.objectId IN (<objectIdList>) AND d.name is not null")
    List<DataSet> getDataSetsWithValidNameForObjectIdList(@BindList("objectIdList") List<String> objectIdList);

    @SqlQuery("select *  from dataset where dataSetId in (<dataSetIds>) ")
    List<DataSet> searchDataSetsByIds(@BindList("dataSetIds") List<Integer> dataSetIds);

    @SqlQuery("select MAX(alias) from dataset")
    Integer findLastAlias();

    /**
     * User -> UserRoles -> DACs -> Consents -> Consent Associations -> DataSets
     *
     * @param email User email
     * @return List of datasets that are visible to the user via DACs.
     */
    @SqlQuery(" select d.* from dataset d " +
            " inner join consentassociations a on d.dataSetId = a.dataSetId " +
            " inner join consents c on a.consentId = c.consentId " +
            " inner join user_role ur on ur.dac_id = c.dac_id " +
            " inner join dacuser u on ur.user_id = u.dacUserId and u.email = :email ")
    List<DataSet> findDataSetsByAuthUserEmail(@Bind("email") String email);

    /**
     * DACs -> Consents -> Consent Associations -> DataSets
     *
     * @return List of datasets that are not owned by a DAC.
     */
    @SqlQuery(" select d.* from dataset d " +
            " inner join consentassociations a on d.dataSetId = a.dataSetId " +
            " inner join consents c on a.consentId = c.consentId " +
            " where c.dac_id is null ")
    List<DataSet> findNonDACDataSets();

    /**
     * DACs -> Consents -> Consent Associations -> DataSets
     * DataSets -> DatasetProperties -> Dictionary
     *
     * @return Set of datasets, with properties, that are associated to a single DAC.
     */
    @UseRowMapper(DataSetPropertiesMapper.class)
    @SqlQuery("select d.*, k.key, p.propertyValue, c.consentId , c.translatedUseRestriction from dataset d " +
            " left outer join datasetproperty p on p.dataSetId = d.dataSetId " +
            " left outer join dictionary k on k.keyId = p.propertyKey " +
            " inner join consentassociations a on a.dataSetId = d.dataSetId " +
            " inner join consents c on c.consentId = a.consentId " +
            " where c.dac_id = :dacId ")
    Set<DataSetDTO> findDatasetsByDac(@Bind("dacId") Integer dacId);

    /**
     * DACs -> Consents -> Consent Associations -> DataSets
     *
     * @return List of dataset id and its associated dac id
     */
    @RegisterRowMapper(ImmutablePairOfIntsMapper.class)
    @SqlQuery("select distinct d.dataSetId, c.dac_id from dataset d " +
            " inner join consentassociations a on d.dataSetId = a.dataSetId " +
            " inner join consents c on a.consentId = c.consentId " +
            " where c.dac_id is not null ")
    List<Pair<Integer, Integer>> findDatasetAndDacIds();

    /**
     * Find the Dac for this dataset.
     *
     * DACs -> Consents -> Consent Associations -> DataSets
     *
     * @param datasetId The dataset Id
     * @return The DAC that corresponds to this dataset
     */
    @RegisterRowMapper(DacMapper.class)
    @SqlQuery("select d.* from dac d " +
            " inner join consents c on d.dac_id = c.dac_id " +
            " inner join consentassociations a on a.consentId = c.consentId " +
            " where a.dataSetId = :datasetId " +
            " limit 1 ")
    Dac findDacForDataset(@Bind("datasetId") Integer datasetId);

    /**
     * Find the Dacs for these dataset ids.
     *
     * DACs -> Consents -> Consent Associations -> DataSets
     *
     * @param datasetIds The dataset Ids
     * @return The DACs that own these datasets
     */
    @RegisterRowMapper(DacMapper.class)
    @SqlQuery("select d.* from dac d " +
            " inner join consents c on d.dac_id = c.dac_id " +
            " inner join consentassociations a on a.consentid = c.consentid " +
            " where a.datasetid in (<datasetids>) " +
            " limit 1 ")
    List<Dac> findDacsForDatasetIds(@BindList("datasetIds") List<Integer> datasetIds);

    @UseRowMapper(DataSetMapper.class)
    @SqlQuery("select d.* from dataset d " +
            " inner join consentassociations a on a.dataSetId = d.dataSetId and a.consentId = :consentId " +
            " where d.active = true ")
    Set<DataSet> findDatasetsForConsentId(@Bind("consentId") String consentId);

}
