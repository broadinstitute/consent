DELETE FROM datasetproperty WHERE dataSetId IN ( Select dataSetId FROM dataset WHERE objectId IN('SC-20657'));
DELETE FROM datasetproperty WHERE dataSetId IN ( Select dataSetId FROM dataset WHERE objectId IN('SC-20658'));
DELETE FROM datasetproperty WHERE dataSetId IN ( Select dataSetId FROM dataset WHERE objectId IN('SC-20659'));
DELETE FROM datasetproperty WHERE dataSetId IN ( Select dataSetId FROM dataset WHERE objectId IN('SC-20659'));
DELETE FROM datasetproperty WHERE dataSetId IN ( Select dataSetId FROM dataset WHERE objectId IN('SC-20659'));
DELETE FROM vote WHERE voteId IN (2000);
DELETE FROM election WHERE electionId IN (130);
DELETE FROM consents WHERE consentId IN ('testId4');
DELETE FROM dataset WHERE datasetId IN (1, 2);
DELETE FROM dataset WHERE objectId IN ('SC-20657', 'SC-20659', 'SC-20658', 'SC-20660');

