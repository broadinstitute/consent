package org.broadinstitute.consent.http.db;


import org.broadinstitute.consent.http.models.DACUserRole;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.unstable.BindIn;

import java.util.List;


@UseStringTemplate3StatementLocator
@RegisterMapper({RoleMapper.class})
public interface DACUserRoleDAO extends Transactional<DACUserRoleDAO> {


    @SqlQuery("select * from roles r inner join user_role du on du.roleId = r.roleId  where du.dacUserId = :userId")
    List<DACUserRole> findRolesByUserId(@Bind("userId") Integer userId);


    @SqlQuery("select roleId from roles where name in (<rolesName>)")
    List<Integer> findRolesIdByName(@BindIn("rolesName") List<String> rolesName);


    @SqlQuery("select roleId from roles where name = :roleName")
    Integer findRoleIdByName(@Bind("roleName") String roleName);


    @SqlBatch("insert into user_role (roleId, dacUserId, email_preference) values (:roleId, :dacUserId, :emailPreference)")
    void insertUserRoles(@BindBean List<DACUserRole> roles, @Bind("dacUserId") Integer dacUserId);


    @SqlUpdate("update user_role set roleId = :newRoleId where dacUserId = :dacUserId and roleId = :existentRoleId")
    void updateUserRoles(@Bind("newRoleId") Integer newRoleId,
                         @Bind("dacUserId") Integer dacUserId,
                         @Bind("existentRoleId") Integer existentRoleId);

    @SqlUpdate("delete  from user_role where dacUserId = :dacUserId")
    void removeRolesByUser(@Bind("dacUserId") Integer dacUserId);

    @SqlQuery("select  r.roleId from roles r inner join user_role du on du.roleId = r.roleId  where du.dacUserId = :userId and r.name = :name")
    Integer findRoleByNameAndUser(@Bind("name") String name, @Bind("userId") Integer id);

}