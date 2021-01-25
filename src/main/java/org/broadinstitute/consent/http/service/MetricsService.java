package org.broadinstitute.consent.http.service;

import com.google.inject.Inject;
import org.broadinstitute.consent.http.db.DataSetDAO;
import org.broadinstitute.consent.http.db.MetricsDAO;
import org.broadinstitute.consent.http.enumeration.ElectionType;
import org.broadinstitute.consent.http.models.Type;
import org.broadinstitute.consent.http.models.dto.DataSetDTO;
import org.broadinstitute.consent.http.models.Dac;
import org.broadinstitute.consent.http.models.DacDecisionMetrics;
import org.broadinstitute.consent.http.models.DarDecisionMetrics;
import org.broadinstitute.consent.http.models.DataAccessRequest;
import org.broadinstitute.consent.http.models.DataAccessRequestData;
import org.broadinstitute.consent.http.models.DataSet;
import org.broadinstitute.consent.http.models.Election;
import org.broadinstitute.consent.http.models.Match;
import org.broadinstitute.consent.http.models.DecisionMetrics;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetricsService {

  public static final String JOINER = "\t";

  private final DacService dacService;
  private final DataSetDAO dataSetDAO;
  private final MetricsDAO metricsDAO;

  @Inject
  public MetricsService(DacService dacService, DataSetDAO dataSetDAO, MetricsDAO metricsDAO) {
    this.dacService = dacService;
    this.dataSetDAO = dataSetDAO;
    this.metricsDAO = metricsDAO;
  }

  public String getHeaderRow(Type type) {
    switch (type) {
      case DAR:
        return String.join(
            JOINER,
            "DAR ID",
            "DAC ID",
            "Dataset ID",
            "Date Submitted",
            "Date Approved",
            "Date Denied",
            "DAR ToT",
            "Dac Decision",
            "Algorithm Decision",
            "Structured Research Purpose Decision",
            "\n");
      case DAC:
        return String.join(
            JOINER,
            "DAC ID",
            "# of DAC Members",
            "# of DAC Chairs",
            "# of Datasets",
            "# of DARs Received",
            "% of DARs Reviewed",
            "Average DAR Turnaround Time",
            "% Reveal DUOS Algorithm",
            "% Agreement with DUOS Algorithm",
            "% Structured Research Purpose Accurate",
            "\n");
      default:
        return "\n";
    }
  }

  public List<? extends DecisionMetrics> generateDecisionMetrics(Type type) {
    List<DataAccessRequest> dars = metricsDAO.findAllDars();
    List<String> referenceIds =
      dars.stream().map(DataAccessRequest::getReferenceId).collect(Collectors.toList());
    List<Integer> datasetIds =
      dars.stream()
        .map(DataAccessRequest::getData)
        .map(DataAccessRequestData::getDatasetIds)
        .flatMap(List::stream)
        .collect(Collectors.toList());
    List<DataSet> datasets = metricsDAO.findDatasetsByIds(datasetIds);
    List<Election> elections = metricsDAO.findLastElectionsByReferenceIds(referenceIds);
    List<Match> matches = metricsDAO.findMatchesForPurposeIds(referenceIds);
    List<Integer> electionIds =
      elections.stream().map(Election::getElectionId).collect(Collectors.toList());
    List<Dac> dacs = metricsDAO.findAllDacsForElectionIds(electionIds);
    List<DarDecisionMetrics> darMetrics = dars.stream()
      .map(
        dataAccessRequest -> {
          Integer datasetId =
            dataAccessRequest.getData().getDatasetIds().stream().findFirst().orElse(0);

          DataSet dataset =
            datasets.stream()
              .filter(d -> d.getDataSetId().equals(datasetId))
              .findFirst()
              .orElse(null);

          Optional<Election> accessElection =
            elections.stream()
              .filter(
                e ->
                  e.getReferenceId()
                    .equalsIgnoreCase(dataAccessRequest.getReferenceId()))
              .filter(
                e ->
                  e.getElectionType()
                    .equalsIgnoreCase(ElectionType.DATA_ACCESS.getValue()))
              .findFirst();

          Optional<Election> rpElection =
            elections.stream()
              .filter(
                e ->
                  e.getReferenceId()
                    .equalsIgnoreCase(dataAccessRequest.getReferenceId()))
              .filter(e -> e.getElectionType().equalsIgnoreCase(ElectionType.RP.getValue()))
              .findFirst();

          Optional<Match> match =
            matches.stream()
              .filter(
                m -> m.getPurpose().equalsIgnoreCase(dataAccessRequest.getReferenceId()))
              .findFirst();

          Optional<Dac> dac =
            accessElection
              .map(
                election ->
                  dacs.stream()
                    .filter(
                      d -> d.getElectionIds().contains(election.getElectionId()))
                    .findFirst())
              .flatMap(Function.identity());

          return new DarDecisionMetrics(
            dataAccessRequest,
            dac.orElse(null),
            dataset,
            accessElection.orElse(null),
            rpElection.orElse(null),
            match.orElse(null));
        })
      .collect(Collectors.toList());
    if (type == Type.DAR) {
      return darMetrics;
    } else {
      List<Dac> allDacs = dacService.findAllDacsWithMembers();
      Set<DataSetDTO> datasetsDacs = dataSetDAO.findDatasetsWithDacs();
      return allDacs.stream()
        .map(
          dac -> {
            List<DarDecisionMetrics> dacFilteredMetrics =
              darMetrics.stream()
                .filter(m -> Objects.nonNull(m.getDacName()))
                .filter(m -> m.getDacName().equalsIgnoreCase(dac.getName()))
                .collect(Collectors.toList());
            List<DataSetDTO> dacFilteredDatasets =
              datasetsDacs.stream()
                .filter(ds -> ds.getDacId().equals(dac.getDacId()))
                .collect(Collectors.toList());
            return new DacDecisionMetrics(dac, dacFilteredDatasets, dacFilteredMetrics);
          })
        .collect(Collectors.toList());
    }
  }
}
