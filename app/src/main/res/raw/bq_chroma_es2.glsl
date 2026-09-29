precision mediump float;
uniform sampler2D uTexSampler;
uniform vec3 uKeyColor;
uniform float uSimilarity;
uniform float uSoftness;
uniform float uSpill;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  float distanceToKey = distance(src.rgb, uKeyColor);
  float edge = max(uSoftness, 0.0001);
  float alpha = smoothstep(uSimilarity, uSimilarity + edge, distanceToKey);
  vec3 chromaSafe = mix(src.rgb, vec3(dot(src.rgb, vec3(0.299,0.587,0.114))), uSpill * (1.0 - alpha));
  gl_FragColor = vec4(chromaSafe, src.a * alpha);
}