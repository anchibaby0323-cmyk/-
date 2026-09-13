#include <jni.h>
#include <android/log.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <poll.h>
#include <errno.h>
#include <string.h>
#include <atomic>
#include <thread>
#include <mutex>
#include <string>

#define LOG_TAG "SwitchProBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_running(false);
static std::thread g_thread;
static std::mutex g_status_mutex;
static std::string g_status = "尚未啟動";

static void set_status(const std::string& s){ std::lock_guard<std::mutex> l(g_status_mutex); g_status=s; }
static std::string get_status(){ std::lock_guard<std::mutex> l(g_status_mutex); return g_status; }
static bool bit_test(const unsigned char* b,int c){ return (b[c/8]&(1u<<(c%8)))!=0; }
static bool write_event(int fd,uint16_t t,uint16_t c,int32_t v){ input_event e{}; e.type=t;e.code=c;e.value=v; return write(fd,&e,sizeof(e))==(ssize_t)sizeof(e); }

static int open_switch_pro(std::string* outPath){
 for(int i=0;i<64;i++){
  std::string p="/dev/input/event"+std::to_string(i); int fd=open(p.c_str(),O_RDONLY|O_NONBLOCK); if(fd<0) continue;
  input_id id{}; if(ioctl(fd,EVIOCGID,&id)!=0||id.vendor!=0x057e||id.product!=0x2009){close(fd);continue;}
  unsigned char ev[(EV_MAX+8)/8]{}; if(ioctl(fd,EVIOCGBIT(0,sizeof(ev)),ev)<0||!bit_test(ev,EV_KEY)){close(fd);continue;}
  unsigned char key[(KEY_MAX+8)/8]{}; if(ioctl(fd,EVIOCGBIT(EV_KEY,sizeof(key)),key)<0||!bit_test(key,BTN_SOUTH)){close(fd);continue;}
  if(outPath)*outPath=p; return fd;
 } return -1;
}
static bool set_abs_from_source(int s,int o,int code,int mn,int mx,int flat){ if(ioctl(o,UI_SET_ABSBIT,code)<0)return false; input_absinfo a{}; if(ioctl(s,EVIOCGABS(code),&a)<0){a.minimum=mn;a.maximum=mx;a.flat=flat;} uinput_abs_setup u{};u.code=code;u.absinfo=a;return ioctl(o,UI_ABS_SETUP,&u)==0; }
static bool set_abs_manual(int o,int code,int mn,int mx){ if(ioctl(o,UI_SET_ABSBIT,code)<0)return false;uinput_abs_setup u{};u.code=code;u.absinfo.minimum=mn;u.absinfo.maximum=mx;return ioctl(o,UI_ABS_SETUP,&u)==0; }
static int create_virtual_xbox(int s,std::string* err){
 int fd=open("/dev/uinput",O_WRONLY|O_NONBLOCK); if(fd<0){if(err)*err=std::string("無法開啟 /dev/uinput：")+strerror(errno);return -1;}
 auto fail=[&](const std::string&w){if(err)*err=w+"："+strerror(errno);close(fd);return -1;};
 if(ioctl(fd,UI_SET_EVBIT,EV_KEY)<0||ioctl(fd,UI_SET_EVBIT,EV_ABS)<0||ioctl(fd,UI_SET_EVBIT,EV_SYN)<0)return fail("設定事件能力失敗");
 const int bs[]={BTN_SOUTH,BTN_EAST,BTN_NORTH,BTN_WEST,BTN_TL,BTN_TR,BTN_SELECT,BTN_START,BTN_MODE,BTN_THUMBL,BTN_THUMBR}; for(int c:bs)if(ioctl(fd,UI_SET_KEYBIT,c)<0)return fail("設定虛擬按鍵失敗");
 if(!set_abs_from_source(s,fd,ABS_X,-32768,32767,500)||!set_abs_from_source(s,fd,ABS_Y,-32768,32767,500)||!set_abs_from_source(s,fd,ABS_RX,-32768,32767,500)||!set_abs_from_source(s,fd,ABS_RY,-32768,32767,500)||!set_abs_manual(fd,ABS_Z,0,255)||!set_abs_manual(fd,ABS_RZ,0,255)||!set_abs_from_source(s,fd,ABS_HAT0X,-1,1,0)||!set_abs_from_source(s,fd,ABS_HAT0Y,-1,1,0))return fail("設定虛擬搖桿軸失敗");
 uinput_setup u{};u.id.bustype=BUS_USB;u.id.vendor=0x045e;u.id.product=0x028e;u.id.version=0x0114;strncpy(u.name,"Microsoft X-Box 360 pad",UINPUT_MAX_NAME_SIZE-1);if(ioctl(fd,UI_DEV_SETUP,&u)<0)return fail("UI_DEV_SETUP 失敗");if(ioctl(fd,UI_DEV_CREATE)<0)return fail("UI_DEV_CREATE 失敗");usleep(180000);return fd;
}
static bool supported(int c){switch(c){case BTN_SOUTH:case BTN_EAST:case BTN_NORTH:case BTN_WEST:case BTN_TL:case BTN_TR:case BTN_SELECT:case BTN_START:case BTN_MODE:case BTN_THUMBL:case BTN_THUMBR:return true;default:return false;}}

static bool run_session(int src,const std::string& path){
 if(ioctl(src,EVIOCGRAB,1)<0){set_status(std::string("等待重新抓取手把：EVIOCGRAB 失敗：")+strerror(errno));return false;}
 std::string err;int out=create_virtual_xbox(src,&err);if(out<0){ioctl(src,EVIOCGRAB,0);set_status("等待重建虛擬手把："+err);return false;}
 write_event(out,EV_ABS,ABS_Z,0);write_event(out,EV_ABS,ABS_RZ,0);write_event(out,EV_SYN,SYN_REPORT,0);
 set_status("修正已啟動 ✅\n來源："+path+"\n自動重連 watchdog：開啟\n原手把已 EVIOCGRAB\n輸出：虛擬 Xbox 360 Gamepad\n映射：實體 A→A、B→B、X→X、Y→Y");
 pollfd p{};p.fd=src;p.events=POLLIN;int hx=0,hy=0;
 while(g_running.load()){
  int pr=poll(&p,1,150);if(pr<0){if(errno==EINTR)continue;break;}if(pr==0)continue;
  if(p.revents&(POLLERR|POLLHUP|POLLNVAL)){set_status("手把連線中斷，watchdog 正在重新連線…");break;}
  if(!(p.revents&POLLIN))continue;
  input_event e{};ssize_t n;
  while((n=read(src,&e,sizeof(e)))==(ssize_t)sizeof(e)){
   bool ok=true;
   if(e.type==EV_KEY){int c=e.code;if(c==BTN_SOUTH)c=BTN_EAST;else if(c==BTN_EAST)c=BTN_SOUTH;
    if(e.code==BTN_TL2)ok=write_event(out,EV_ABS,ABS_Z,e.value?255:0);else if(e.code==BTN_TR2)ok=write_event(out,EV_ABS,ABS_RZ,e.value?255:0);
    else if(e.code==KEY_UP){hy=e.value?-1:0;ok=write_event(out,EV_ABS,ABS_HAT0Y,hy);}else if(e.code==KEY_DOWN){hy=e.value?1:0;ok=write_event(out,EV_ABS,ABS_HAT0Y,hy);}else if(e.code==KEY_LEFT){hx=e.value?-1:0;ok=write_event(out,EV_ABS,ABS_HAT0X,hx);}else if(e.code==KEY_RIGHT){hx=e.value?1:0;ok=write_event(out,EV_ABS,ABS_HAT0X,hx);}else if(supported(c))ok=write_event(out,EV_KEY,(uint16_t)c,e.value);
   }else if(e.type==EV_ABS){switch(e.code){case ABS_X:case ABS_Y:case ABS_RX:case ABS_RY:case ABS_HAT0X:case ABS_HAT0Y:ok=write_event(out,EV_ABS,e.code,e.value);break;default:break;}}
   else if(e.type==EV_SYN&&e.code==SYN_REPORT)ok=write_event(out,EV_SYN,SYN_REPORT,0);
   if(!ok){set_status("虛擬手把輸出中斷，watchdog 正在重建…");goto end_session;}
  }
  if(n==0||(n<0&&errno!=EAGAIN&&errno!=EWOULDBLOCK)){set_status("手把輸入中斷，watchdog 正在重新連線…");break;}
  if(p.revents&(POLLERR|POLLHUP|POLLNVAL))break;
 }
end_session:
 ioctl(out,UI_DEV_DESTROY);close(out);ioctl(src,EVIOCGRAB,0);return true;
}

static void bridge_loop(){
 int attempts=0;
 while(g_running.load()){
  std::string path;int src=open_switch_pro(&path);
  if(src<0){attempts++;set_status("等待 Switch Pro 重新連線…\nwatchdog 重試："+std::to_string(attempts));usleep(500000);continue;}
  attempts=0;run_session(src,path);close(src);
  if(g_running.load())usleep(350000);
 }
 set_status("修正已停止。原始 Switch Pro 輸入已恢復。");
}
static std::string diagnostics_text(){std::string r="native uid："+std::to_string(getuid())+"\n";std::string p;int s=open_switch_pro(&p);if(s>=0){char n[256]{};ioctl(s,EVIOCGNAME(sizeof(n)),n);r+="Switch Pro：已找到\n來源："+p+"\n名稱："+std::string(n[0]?n:"Nintendo Switch Pro Controller")+"\n";close(s);}else r+="Switch Pro：找不到 057e:2009 gamepad event\n";int u=open("/dev/uinput",O_WRONLY|O_NONBLOCK);if(u>=0){r+="/dev/uinput：可開啟 ✅\n";close(u);}else r+=std::string("/dev/uinput：無法開啟 ❌ (")+strerror(errno)+")\n";r+="橋接狀態："+get_status();return r;}
static jstring js(JNIEnv*e,const std::string&s){return e->NewStringUTF(s.c_str());}
extern "C" JNIEXPORT jstring JNICALL Java_com_example_switchprokeyfix_BridgeNative_diagnostics(JNIEnv*e,jobject){return js(e,diagnostics_text());}
extern "C" JNIEXPORT jstring JNICALL Java_com_example_switchprokeyfix_BridgeNative_startBridge(JNIEnv*e,jobject){if(g_running.load())return js(e,get_status());if(g_thread.joinable())g_thread.join();g_running=true;set_status("正在啟動橋接 watchdog…");g_thread=std::thread(bridge_loop);usleep(300000);return js(e,get_status());}
extern "C" JNIEXPORT jstring JNICALL Java_com_example_switchprokeyfix_BridgeNative_stopBridge(JNIEnv*e,jobject){g_running=false;if(g_thread.joinable())g_thread.join();return js(e,get_status());}
