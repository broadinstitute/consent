package org.broadinstitute.consent.http.db;

import org.broadinstitute.consent.http.models.DACUser;
import org.broadinstitute.consent.http.models.Dac;
import org.broadinstitute.consent.http.models.Role;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;

import java.util.Date;
import java.util.List;

@UseStringTemplate3StatementLocator
@RegisterMapper({DacMapper.class})
public interface DacDAO extends Transactional<DacDAO> {

    @SqlQuery("select * from dac")
    List<Dac> findAll();

    @SqlQuery("select * from dac where dac_id = :dacId")
    Dac findById(@Bind("dacId") Integer dacId);

    @SqlUpdate("insert into dac (name, description, create_date) values (:name, :description, :createDate)")
    @GetGeneratedKeys
    Integer createDac(@Bind("name") String name, @Bind("description") String description, @Bind("createDate") Date createDate);

    @SqlUpdate("update dac set name = :name, description = :description, update_date = :updateDate where dac_id = :dacId")
    void updateDac(
            @Bind("name") String name,
            @Bind("description") String description,
            @Bind("updateDate") Date updateDate,
            @Bind("dacId") Integer dacId);

    @SqlUpdate("delete from user_role where dac_id = :dacId")
    void deleteDacMembers(@Bind("dacId") Integer dacId);

    @SqlUpdate("delete from dac where dac_id = :dacId")
    void deleteDac(@Bind("dacId") Integer dacId);

    @Mapper(DACUserRoleMapper.class)
    @SqlQuery("select du.*, r.roleId, r.name, ur.dac_id from dacuser du " +
              "inner join user_role ur on ur.user_id = du.dacUserId " +
              "inner join roles r on ur.role_id = r.roleId " +
              "inner join dac d on d.dac_id = ur.dac_id " +
              "where d.dac_id = :dacId")
    List<DACUser> findMembersByDacId(@Bind("dacId") Integer dacId);

    @SqlUpdate("insert into user_role (role_id, user_id, dac_id) values (:roleId, :userId, :dacId)")
    void addDacMember(@Bind("roleId") Integer roleId, @Bind("userId") Integer userId, @Bind("dacId") Integer dacId);

    @SqlUpdate("delete from user_role where user_role_id = :userRoleId")
    void removeDacMember(@Bind("userRoleId") Integer userRoleId);

    @Mapper(RoleMapper.class)
    @SqlQuery("select * from roles where roleId = :roleId")
    Role getRoleById(@Bind("roleId") Integer roleId);

    @Mapper(DACUserRoleMapper.class)
    @SqlQuery("select du.*, r.roleId, r.name, ur.dac_id from dacuser du left join user_role ur on ur.user_id = :dacUserId left join roles r on r.roleId = ur.role_id where du.dacUserId = :dacUserId")
    DACUser findUserById(@Bind("dacUserId") Integer dacUserId);

}
