package com.scrumble.gudocs.ocr.client;

import java.util.List;

record ClovaOcrRequest(String version, String requestId, long timestamp, List<Image> images) {

    record Image(String format, String name, String data) {
    }
}
