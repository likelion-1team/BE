package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest.Selection;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.ApplicationContext;

public class LegacyAnalysisWorkerTestDriver {
    private final ApplicationContext context;

    public LegacyAnalysisWorkerTestDriver(ApplicationContext context) {
        this.context = context;
    }

    public void run(Long uploadId, String fileName, LocalDate date, String externalId, Long taskTypeId) {
        context.getBean(AnalysisWorker.class).run(new AnalysisJob(uploadId, fileName,
                new UploadFileStorage.TargetPeriod(date, date), List.of(new Selection(externalId, taskTypeId))));
    }
}
