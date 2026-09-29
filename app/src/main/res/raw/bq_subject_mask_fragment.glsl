precision mediump float;
uniform sampler2D uTex;
uniform float uMaskAverage;
uniform float uThreshold;
varying vec2 vTexCoord;
void main(){
  vec4 c=texture2D(uTex,vTexCoord);
  float keep=smoothstep(max(0.0,uThreshold-0.15),min(1.0,uThreshold+0.15),uMaskAverage);
  gl_FragColor=vec4(c.rgb,c.a*keep);
}