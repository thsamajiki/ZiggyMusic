#ifndef Header_AudioEffectsChain
#define Header_AudioEffectsChain

#pragma once
#include "Superpowered.h"
#include "SuperpoweredFilter.h"
#include "SuperpoweredReverb.h"
#include <vector>
#include <memory>
#include <mutex>

class AudioDspChain {
public:
    explicit AudioDspChain(unsigned int sampleRate);
    ~AudioDspChain();

    // process: interleaved stereo float buffer in-place
    void process(float *bufferInterleaved, unsigned int numberOfFrames, unsigned int sampleRate);
    void setEQBand(int bandIndex, float gainDb); // bandIndex: 0..(numBands-1)
    void setCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb);
    void setReverb(bool enabled, float wet);

private:
    unsigned int currentSampleRate;
    unsigned int numBands;
    // Filters as unique_ptr -> 자동 소멸
    std::vector<std::unique_ptr<Superpowered::Filter>> eqBands;
    std::unique_ptr<Superpowered::Reverb> reverb;

    // preallocated buffers to avoid per-callback allocations
    unsigned int maxFramesAllocated{};
    std::vector<float> leftBuffer;
    std::vector<float> rightBuffer;
    std::vector<float> interleavedTemp; // used for reverb / mixing

    // compressor state
    float compThreshold;
    float compRatio;
    float compAttackCoeff, compReleaseCoeff;
    float compGainMakeup;
    float compEnv;

    bool reverbEnabled;
    float reverbWet;

    void applyEQ(float *buffer, unsigned int frames);
    void applyCompressor(float *buffer, unsigned int frames);
};

#endif
