package com.example.livetranscription.service;

import java.nio.ByteBuffer;

public interface TranscriptionService {
    void sendAudio(ByteBuffer pcm16Chunk) throws Exception;
    void close();
}
