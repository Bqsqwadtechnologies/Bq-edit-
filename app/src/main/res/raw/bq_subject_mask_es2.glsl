precision mediump float;
uniform sampler2D uTexSampler;
uniform sampler2D uMaskSampler;
uniform float uFeather;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  float mask = texture2D(uMaskSampler, vTexSamplingCoord).r;
  float edge = max(uFeather, 0.001);
  float alpha = smoothstep(0.0, edge + 0.001, mask);
  gl_FragColor = vec4(src.rgb, src.a * alpha);
}