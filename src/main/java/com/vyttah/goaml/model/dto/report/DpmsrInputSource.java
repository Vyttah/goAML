package com.vyttah.goaml.model.dto.report;

import com.vyttah.goaml.engine.build.DpmsrReportInput;

/**
 * A source that can produce a DPMSR {@link DpmsrReportInput} for the engine. Lets {@code ReportService}
 * funnel both contracts through one code path: the full-fidelity {@link DpmsrReportPayload} (REST) and the
 * curated {@code DpmsrCreateRequest} (internal programmatic builders, adapted via {@code DpmsrRequestMapper}).
 */
public interface DpmsrInputSource {

    /** The report's {@code entity_reference} — used for the duplicate-report guard and persistence. */
    String entityReference();

    /** Build the engine input, injecting the server-resolved {@code rentity_id}. */
    DpmsrReportInput toInput(int rentityId);
}
