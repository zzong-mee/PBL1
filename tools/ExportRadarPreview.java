// Synthetic three-point scan, using the app's real policy, measured HRIRs, and renderer.
import com.guideeye.app.audio.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
public final class ExportRadarPreview {
 public static void main(String[] args) throws Exception {
  SpatialHrirBank bank=new SpatialHrirBank(Files.readAllBytes(Path.of(args[0])));
  List<SpatialTarget> points=List.of(
   new SpatialTarget("virtual",new TofPoint(0,500,30,0),10000),
   new SpatialTarget("virtual",new TofPoint(1,1500,0,0),10000),
   new SpatialTarget("virtual",new TofPoint(2,3000,-30,0),10000));
  Set<Integer> plan=RadarScanPolicy.INSTANCE.plan(points,0,3);
  Map<Integer,SpatialTarget> candidates=RadarScanPolicy.INSTANCE.candidates(points,0);
  short[] result=new short[44100*4*2];
  for(int cycle=0;cycle<4;cycle++)for(int bin:plan){
   SpatialTarget target=candidates.get(bin);TofPoint point=target.getPoint();
   short[] pulse=bank.render(bank.nearest(point.getAzimuthDegrees(),point.getElevationDegrees()),20);
   float gain=DistanceBeepPolicy.INSTANCE.signal(point.getRangeMm(),null,0).getGain();
   long offsetMs=(bin*1000L+15)/16;
   int offset=(cycle*44100+(int)(offsetMs*44100/1000))*2;
   for(int i=0;i<pulse.length;i++)result[offset+i]=(short)(pulse[i]*gain);
  }
  int length=result.length*2;
  ByteBuffer wav=ByteBuffer.allocate(44+length).order(ByteOrder.LITTLE_ENDIAN);
  wav.put("RIFF".getBytes()).putInt(36+length).put("WAVEfmt ".getBytes()).putInt(16);
  wav.putShort((short)1).putShort((short)2).putInt(44100).putInt(44100*4).putShort((short)4).putShort((short)16);
  wav.put("data".getBytes()).putInt(length);
  for(short value:result)wav.putShort(value);
  Files.write(Path.of(args[1]),wav.array());
  System.out.println("Four 1s scans; virtual points +30deg/0.5m, 0deg/1.5m, -30deg/3m; slots "+plan);
 }
}
