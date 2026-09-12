package com.anchibaby.mtzimporter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
 private static final int PICK=1001, SHI=1002; private final ExecutorService ex=Executors.newSingleThreadExecutor();
 private TextView log,picked,pt; private ProgressBar pb; private Button go; private File mtz; private volatile boolean ready,running;
 private final Shizuku.OnBinderReceivedListener br=()->runOnUiThread(()->{ready=true; add("✓ Shizuku Binder 已連線"); prog(10,"Shizuku 就緒");});
 private final Shizuku.OnBinderDeadListener bd=()->runOnUiThread(()->{ready=false;add("! Shizuku 已中斷");prog(0,"Shizuku 中斷");});
 private final Shizuku.OnRequestPermissionResultListener pr=(r,g)->{if(r==SHI) runOnUiThread(()->add(g==PackageManager.PERMISSION_GRANTED?"✓ Shizuku 已授權":"✗ Shizuku 未授權"));};
 @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(ui());add("版本：0.4.0 diagnostic");add("這版不猜 ThemeDetail URI；會診斷 Xiaomi 對外 Import/Provider/Activity 入口。未找到官方入口時不會卡住個性主題。");Shizuku.addBinderReceivedListenerSticky(br);Shizuku.addBinderDeadListener(bd);Shizuku.addRequestPermissionResultListener(pr);state();}
 @Override protected void onDestroy(){Shizuku.removeBinderReceivedListener(br);Shizuku.removeBinderDeadListener(bd);Shizuku.removeRequestPermissionResultListener(pr);ex.shutdownNow();super.onDestroy();}
 private View ui(){int p=dp(18);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(p,p,p,p);TextView t=new TextView(this);t.setText("MTZ 正式匯入診斷 v0.4");t.setTextSize(24);r.addView(t);TextView d=new TextView(this);d.setText("先診斷 Theme Manager 是否真的提供第三方 App 可呼叫的 Import 入口，再交付 MTZ。這版不會直接打開會卡住的主題詳情頁。");d.setPadding(0,dp(6),0,dp(10));r.addView(d);pt=new TextView(this);pt.setText("0% · 待命");r.addView(pt);pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);r.addView(pb,new LinearLayout.LayoutParams(-1,dp(12)));Button s=btn("1. 檢查 / 授權 Shizuku",v->auth());r.addView(s);Button c=btn("2. 選擇 MTZ",v->choose());r.addView(c);picked=new TextView(this);picked.setText("尚未選擇 MTZ");picked.setPadding(0,dp(6),0,dp(6));r.addView(picked);go=btn("3. 診斷並嘗試官方 Import",v->start());r.addView(go);Button diag=btn("只診斷 Theme Manager 入口",v->diagnoseOnly());r.addView(diag);Button home=btn("開啟個性主題首頁",v->ex.execute(()->cmd("am start -W -n com.android.thememanager/com.android.thememanager.ThemeResourceTabActivity")));r.addView(home);TextView h=new TextView(this);h.setText("\n執行紀錄");h.setTextSize(18);r.addView(h);log=new TextView(this);log.setTextIsSelectable(true);log.setTextSize(12);ScrollView sv=new ScrollView(this);sv.addView(log);r.addView(sv,new LinearLayout.LayoutParams(-1,0,1));return r;}
 private Button btn(String x,View.OnClickListener l){Button b=new Button(this);b.setText(x);b.setOnClickListener(l);return b;}
 private void state(){try{if(!Shizuku.pingBinder()){ready=false;add("! 等待 Shizuku Binder");return;}ready=true;if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){add("✓ Shizuku Binder 正常且已授權");prog(10,"Shizuku 就緒");}else add("! Shizuku 已連線但尚未授權");}catch(Throwable t){add("✗ Shizuku："+err(t));}}
 private void auth(){try{if(!Shizuku.pingBinder()){state();return;}if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){add("✓ 已有 Shizuku 權限");return;}Shizuku.requestPermission(SHI);}catch(Throwable t){add("✗ "+err(t));}}
 private void choose(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK);}
 @Override protected void onActivityResult(int q,int res,Intent data){super.onActivityResult(q,res,data);if(q!=PICK||res!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();String n=name(u);if(n==null)n="selected.mtz";if(!n.toLowerCase().endsWith(".mtz")){Toast.makeText(this,"請選擇 .mtz",Toast.LENGTH_LONG).show();return;}String nn=safe(n);picked.setText("讀取中："+nn);prog(18,"讀取 MTZ");ex.execute(()->stage(u,nn));}
 private void stage(Uri u,String n){try{File dir=new File(getExternalFilesDir(null),"stage");dir.mkdirs();File o=new File(dir,System.currentTimeMillis()+"_"+n);long total=0;try(InputStream in=getContentResolver().openInputStream(u);FileOutputStream f=new FileOutputStream(o)){if(in==null)throw new Exception("無法開啟檔案");byte[] b=new byte[131072];int z;while((z=in.read(b))>=0){f.write(b,0,z);total+=z;}}if(total<64)throw new Exception("MTZ 太小");mtz=o;long sz=total;runOnUiThread(()->picked.setText("已選擇："+n+"\n"+sz+" bytes"));addW("✓ MTZ 已就緒");prog(25,"MTZ 已就緒");}catch(Throwable t){addW("✗ MTZ："+err(t));prog(0,"MTZ 讀取失敗");}}
 private void diagnoseOnly(){if(!ok())return;ex.execute(()->{prog(20,"掃描 Theme Manager");String d=diagnostics();addW(d);prog(100,"診斷完成");});}
 private String diagnostics(){StringBuilder s=new StringBuilder();String[] qs={"cmd package query-activities --brief -a android.intent.action.VIEW -p com.android.thememanager","dumpsys package com.android.thememanager | grep -i -E 'ThemeImport|ImportTheme|ThemeProvider|provider|exported|permission' | head -n 120","cmd package resolve-activity --brief -a android.intent.action.VIEW -t application/zip -p com.android.thememanager","cmd package resolve-activity --brief -a android.intent.action.VIEW -t application/octet-stream -p com.android.thememanager"};for(int i=0;i<qs.length;i++){CommandResult r=cmd(qs[i]);s.append("\n[診斷 ").append(i+1).append("] exit=").append(r.code).append("\n").append(r.out).append('\n');}return s.toString();}
 private void start(){if(running)return;if(mtz==null||!mtz.isFile()){add("✗ 請先選 MTZ");return;}if(!ok())return;running=true;runOnUiThread(()->go.setEnabled(false));ex.execute(()->{String dir="/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.data/import/theme", target=dir+"/chatgpt_"+System.currentTimeMillis()+"_"+safe(mtz.getName());try{prog(32,"診斷官方 Import 入口");String dg=diagnostics();addW(dg);prog(48,"寫入 Import staging");CommandResult cp=cmd("mkdir -p "+q(dir)+" && cp "+q(mtz.getAbsolutePath())+" "+q(target)+" && chmod 0644 "+q(target)+" && test -s "+q(target));if(cp.code!=0){addW("✗ staging 寫入失敗："+cp.out);prog(0,"寫入失敗");return;}addW("✓ staging 寫入成功："+target);prog(62,"測試官方可解析 VIEW 入口");CommandResult mime=cmd("cmd package resolve-activity --brief -a android.intent.action.VIEW -t application/zip -p com.android.thememanager");boolean has=mime.code==0&&mime.out.contains("com.android.thememanager/");if(has){addW("✓ 找到可解析 VIEW Activity："+mime.out);prog(72,"嘗試交給官方 VIEW 入口");CommandResult v=cmd("am start -W -a android.intent.action.VIEW -t application/zip -d file://"+qPath(target)+" -p com.android.thememanager");addW("VIEW exit="+v.code+"\n"+v.out);}else{addW("! Theme Manager 沒有公開可解析的 MTZ/ZIP VIEW Activity。這表示單純 Shizuku 複製不能啟動內部 ThemeImportManager。");}prog(84,"確認 staging 是否被接手");Thread.sleep(2500);CommandResult e=cmd("test -e "+q(target));if(e.code!=0){addW("✓ staging 已被 Theme Manager 移走，Import 有反應");prog(100,"Theme Manager 已接手");}else{addW("✗ staging 仍存在：官方沒有接受這個 MTZ。為避免殘留，現在清除測試檔。");cmd("rm -f "+q(target));prog(100,"官方 Import 未接受 · 已安全清理");}}catch(Throwable t){addW("✗ "+err(t));prog(0,"執行失敗");}finally{running=false;runOnUiThread(()->go.setEnabled(true));}});}
 private boolean ok(){try{if(!ready||!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){add("✗ Shizuku 尚未就緒");auth();return false;}return true;}catch(Throwable t){add("✗ "+err(t));return false;}}
 private CommandResult cmd(String c){StringBuilder o=new StringBuilder();int ec=-1;try{Method m=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);m.setAccessible(true);Process p=(Process)m.invoke(null,new Object[]{new String[]{"sh","-c",c},null,null});try(BufferedReader a=new BufferedReader(new InputStreamReader(p.getInputStream()));BufferedReader b=new BufferedReader(new InputStreamReader(p.getErrorStream()))){String l;while((l=a.readLine())!=null)o.append(l).append('\n');while((l=b.readLine())!=null)o.append(l).append('\n');}ec=p.waitFor();}catch(Throwable t){o.append(err(t));}return new CommandResult(ec,o.toString().trim());}
 private String name(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Throwable ignored){}return null;}
 private void prog(int n,String s){runOnUiThread(()->{pb.setProgress(Math.max(0,Math.min(100,n)));pt.setText(n+"% · "+s);});}private void add(String s){if(log!=null)log.append((log.length()==0?"":"\n")+s);}private void addW(String s){runOnUiThread(()->add(s));}private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}private static String safe(String s){return s.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]","_");}private static String q(String s){return"'"+s.replace("'","'\\''")+"'";}private static String qPath(String s){return s.replace(" ","%20");}private static String err(Throwable t){return t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());}private static class CommandResult{final int code;final String out;CommandResult(int c,String o){code=c;out=o;}}
}
