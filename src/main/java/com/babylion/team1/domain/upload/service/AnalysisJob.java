package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest.Selection;
import java.util.List;

record AnalysisJob(Long uploadId, String fileName, UploadFileStorage.TargetPeriod targetPeriod,
                   List<Selection> selections) {
}
