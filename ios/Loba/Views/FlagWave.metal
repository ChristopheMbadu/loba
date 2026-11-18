#include <metal_stdlib>
using namespace metal;

// Sample the cloth farther from its resting position as it leaves the hoist.
[[ stitchable ]] float2 flagWave(float2 position, float phase) {
    float across = saturate(position.x / 50.0);
    return position + float2(0, 2.5 * across * sin(6.0 * across - phase));
}
