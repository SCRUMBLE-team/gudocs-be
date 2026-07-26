package com.scrumble.gudocs.ocr.client;

public interface ClovaOcrClient {

    /**
     * @param imageBytes 이미지 바이트
     * @param imageFormat "jpg" 또는 "png"
     * @return OCR로 인식된 평문 텍스트(필드를 줄바꿈/공백으로 이어붙인 것)
     */
    String extractText(byte[] imageBytes, String imageFormat);
}
