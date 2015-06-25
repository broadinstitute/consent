insert into consents(consentId, requiresManualReview,useRestriction,active) values ('testId',true,'test',true);
insert into consents(consentId, requiresManualReview,useRestriction,active) values ('testId2',true,'test',true);
insert into consentassociations(associationId, consentId,associationType,objectId) values (100,'testId','associationType',1);
insert into researchpurpose (purposeId,purpose) values(1,'General Use');
insert into dataset (dataSetId,consentId,description) values(1,'testId','test');
insert into datarequest (requestId, purposeId, associationId, dataSetId,description,researcher) values (1,'1',100,'1','test','researcherTest');
insert into datarequest (requestId, purposeId, associationId, dataSetId,description,researcher) values (2,'1',100,'1','test','researcherTest');
insert into dacuser(dacUserId,email,displayName,memberStatus) values(1,'test@broad.com','testUser','');
insert into dacuser(dacUserId,email,displayName,memberStatus) values(2,'test2@broad.com','testUser','');
