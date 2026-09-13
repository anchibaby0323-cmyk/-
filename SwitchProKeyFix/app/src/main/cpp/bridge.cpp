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

static void set_status(const std::string& s) {
    std::lock_guard<std::mutex> lock(g_status_mutex);
    g_status = s;
}

static std::string get_status() {
    std::lock_guard<std::mutex> lock(g_status_mutex);
    return g_status;
}

static bool bit_test(const unsigned char* bits, int code) {
    return (bits[code / 8] & (1u << (code % 8))) != 0;
}

static bool write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    input_event ev{};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    ssize_t n = write(fd, &ev, sizeof(ev));
    return n == static_cast<ssize_t>(sizeof(ev));
}

static int open_switch_pro(std::string* outPath) {
    for (int i = 0; i < 64; ++i) {
        std::string path = "/dev/input/event" + std::to_string(i);
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK);
        if (fd < 0) continue;

        input_id id{};
        if (ioctl(fd, EVIOCGID, &id) != 0 || id.vendor != 0x057e || id.product != 0x2009) {
            close(fd);
            continue;
        }

        unsigned char evBits[(EV_MAX + 8) / 8]{};
        if (ioctl(fd, EVIOCGBIT(0, sizeof(evBits)), evBits) < 0 || !bit_test(evBits, EV_KEY)) {
            close(fd);
            continue;
        }

        unsigned char keyBits[(KEY_MAX + 8) / 8]{};
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBits)), keyBits) < 0 || !bit_test(keyBits, BTN_SOUTH)) {
            close(fd);
            continue;
        }

        if (outPath) *outPath = path;
        return fd;
    }
    return -1;
}

static bool set_abs_from_source(int srcFd, int outFd, int code, int fallbackMin, int fallbackMax, int fallbackFlat) {
    if (ioctl(outFd, UI_SET_ABSBIT, code) < 0) return false;

    input_absinfo info{};
    if (ioctl(srcFd, EVIOCGABS(code), &info) < 0) {
        info.minimum = fallbackMin;
        info.maximum = fallbackMax;
        info.flat = fallbackFlat;
        info.fuzz = 0;
        info.resolution = 0;
    }

    uinput_abs_setup setup{};
    setup.code = code;
    setup.absinfo = info;
    return ioctl(outFd, UI_ABS_SETUP, &setup) == 0;
}

static bool set_abs_manual(int outFd, int code, int min, int max) {
    if (ioctl(outFd, UI_SET_ABSBIT, code) < 0) return false;
    uinput_abs_setup setup{};
    setup.code = code;
    setup.absinfo.minimum = min;
    setup.absinfo.maximum = max;
    setup.absinfo.flat = 0;
    setup.absinfo.fuzz = 0;
    setup.absinfo.resolution = 0;
    return ioctl(outFd, UI_ABS_SETUP, &setup) == 0;
}

static int create_virtual_xbox(int srcFd, std::string* error) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        if (error) *error = std::string("無法開啟 /dev/uinput：") + strerror(errno);
        return -1;
    }

    auto fail = [&](const std::string& why) {
        if (error) *error = why + "：" + strerror(errno);
        close(fd);
        return -1;
    };

    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) return fail("設定 EV_KEY 失敗");
    if (ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0) return fail("設定 EV_ABS 失敗");
    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) return fail("設定 EV_SYN 失敗");

    const int buttons[] = {
        BTN_SOUTH, BTN_EAST, BTN_NORTH, BTN_WEST,
        BTN_TL, BTN_TR,
        BTN_SELECT, BTN_START, BTN_MODE,
        BTN_THUMBL, BTN_THUMBR
    };
    for (int code : buttons) {
        if (ioctl(fd, UI_SET_KEYBIT, code) < 0) return fail("設定虛擬按鍵失敗");
    }

    if (!set_abs_from_source(srcFd, fd, ABS_X, -32768, 32767, 500)) return fail("設定左搖桿 X 失敗");
    if (!set_abs_from_source(srcFd, fd, ABS_Y, -32768, 32767, 500)) return fail("設定左搖桿 Y 失敗");
    if (!set_abs_from_source(srcFd, fd, ABS_RX, -32768, 32767, 500)) return fail("設定右搖桿 X 失敗");
    if (!set_abs_from_source(srcFd, fd, ABS_RY, -32768, 32767, 500)) return fail("設定右搖桿 Y 失敗");
    if (!set_abs_manual(fd, ABS_Z, 0, 255)) return fail("設定左扳機失敗");
    if (!set_abs_manual(fd, ABS_RZ, 0, 255)) return fail("設定右扳機失敗");
    if (!set_abs_from_source(srcFd, fd, ABS_HAT0X, -1, 1, 0)) return fail("設定方向鍵 X 失敗");
    if (!set_abs_from_source(srcFd, fd, ABS_HAT0Y, -1, 1, 0)) return fail("設定方向鍵 Y 失敗");

    uinput_setup setup{};
    setup.id.bustype = BUS_USB;
    setup.id.vendor = 0x045e;
    setup.id.product = 0x028e;
    setup.id.version = 0x0114;
    strncpy(setup.name, "Microsoft X-Box 360 pad", UINPUT_MAX_NAME_SIZE - 1);

    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0) return fail("UI_DEV_SETUP 失敗");
    if (ioctl(fd, UI_DEV_CREATE) < 0) return fail("UI_DEV_CREATE 失敗");

    usleep(180000);
    return fd;
}

static bool output_key_is_supported(int code) {
    switch (code) {
        case BTN_SOUTH:
        case BTN_EAST:
        case BTN_NORTH:
        case BTN_WEST:
        case BTN_TL:
        case BTN_TR:
        case BTN_SELECT:
        case BTN_START:
        case BTN_MODE:
        case BTN_THUMBL:
        case BTN_THUMBR:
            return true;
        default:
            return false;
    }
}

static void bridge_loop() {
    std::string sourcePath;
    int src = open_switch_pro(&sourcePath);
    if (src < 0) {
        set_status("啟動失敗：找不到 Switch Pro HAC-013（057e:2009）的 gamepad event 裝置。\n請先用 USB 或藍牙連接手把。 ");
        g_running = false;
        return;
    }

    if (ioctl(src, EVIOCGRAB, 1) < 0) {
        set_status(std::string("啟動失敗：無法 EVIOCGRAB 抓取原手把：") + strerror(errno) + "\n來源：" + sourcePath);
        close(src);
        g_running = false;
        return;
    }

    std::string createError;
    int out = create_virtual_xbox(src, &createError);
    if (out < 0) {
        ioctl(src, EVIOCGRAB, 0);
        close(src);
        set_status("啟動失敗：" + createError + "\n來源：" + sourcePath);
        g_running = false;
        return;
    }

    write_event(out, EV_ABS, ABS_Z, 0);
    write_event(out, EV_ABS, ABS_RZ, 0);
    write_event(out, EV_SYN, SYN_REPORT, 0);

    set_status(
        "修正已啟動 ✅\n"
        "來源：" + sourcePath + "\n"
        "原手把已 EVIOCGRAB，避免雙重輸入。\n"
        "輸出：虛擬 Xbox 360 Gamepad\n"
        "最終映射：實體 A→A、B→B、X→X、Y→Y\n"
        "（內部只需要交換 raw BTN_SOUTH/BTN_EAST；X/Y 由虛擬 Xbox 的標準 KL 自然校正。）"
    );

    int hatX = 0;
    int hatY = 0;

    pollfd pfd{};
    pfd.fd = src;
    pfd.events = POLLIN;

    while (g_running.load()) {
        int pr = poll(&pfd, 1, 100);
        if (pr < 0) {
            if (errno == EINTR) continue;
            set_status(std::string("橋接中斷：poll 失敗：") + strerror(errno));
            break;
        }
        if (pr == 0 || !(pfd.revents & POLLIN)) continue;

        input_event ev{};
        while (read(src, &ev, sizeof(ev)) == static_cast<ssize_t>(sizeof(ev))) {
            if (ev.type == EV_KEY) {
                int code = ev.code;

                // Switch Pro raw labels -> Xbox/Linux canonical labels.
                // Physical B reports BTN_SOUTH and physical A reports BTN_EAST.
                if (code == BTN_SOUTH) code = BTN_EAST;      // physical B -> Android B
                else if (code == BTN_EAST) code = BTN_SOUTH; // physical A -> Android A

                // ZL / ZR are digital buttons on Switch Pro; Xbox Android layout expects trigger axes.
                if (ev.code == BTN_TL2) {
                    write_event(out, EV_ABS, ABS_Z, ev.value ? 255 : 0);
                    continue;
                }
                if (ev.code == BTN_TR2) {
                    write_event(out, EV_ABS, ABS_RZ, ev.value ? 255 : 0);
                    continue;
                }

                // Older/newer hid-nintendo variants may expose d-pad as keys instead of HAT axes.
                if (ev.code == KEY_UP) {
                    hatY = ev.value ? -1 : 0;
                    write_event(out, EV_ABS, ABS_HAT0Y, hatY);
                    continue;
                }
                if (ev.code == KEY_DOWN) {
                    hatY = ev.value ? 1 : 0;
                    write_event(out, EV_ABS, ABS_HAT0Y, hatY);
                    continue;
                }
                if (ev.code == KEY_LEFT) {
                    hatX = ev.value ? -1 : 0;
                    write_event(out, EV_ABS, ABS_HAT0X, hatX);
                    continue;
                }
                if (ev.code == KEY_RIGHT) {
                    hatX = ev.value ? 1 : 0;
                    write_event(out, EV_ABS, ABS_HAT0X, hatX);
                    continue;
                }

                if (output_key_is_supported(code)) {
                    write_event(out, EV_KEY, static_cast<uint16_t>(code), ev.value);
                }
            } else if (ev.type == EV_ABS) {
                switch (ev.code) {
                    case ABS_X:
                    case ABS_Y:
                    case ABS_RX:
                    case ABS_RY:
                    case ABS_HAT0X:
                    case ABS_HAT0Y:
                        write_event(out, EV_ABS, ev.code, ev.value);
                        break;
                    default:
                        break;
                }
            } else if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
                write_event(out, EV_SYN, SYN_REPORT, 0);
            }
        }
    }

    ioctl(out, UI_DEV_DESTROY);
    close(out);
    ioctl(src, EVIOCGRAB, 0);
    close(src);

    if (!g_running.load()) {
        set_status("修正已停止。原始 Switch Pro 輸入已恢復。 ");
    }
    g_running = false;
}

static std::string diagnostics_text() {
    std::string result;
    result += "native uid：" + std::to_string(getuid()) + "\n";

    std::string path;
    int src = open_switch_pro(&path);
    if (src >= 0) {
        char name[256]{};
        ioctl(src, EVIOCGNAME(sizeof(name)), name);
        result += "Switch Pro：已找到\n";
        result += "來源：" + path + "\n";
        result += "名稱：" + std::string(name[0] ? name : "Nintendo Switch Pro Controller") + "\n";
        close(src);
    } else {
        result += "Switch Pro：找不到 057e:2009 gamepad event\n";
    }

    int ufd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (ufd >= 0) {
        result += "/dev/uinput：可開啟 ✅\n";
        close(ufd);
    } else {
        result += std::string("/dev/uinput：無法開啟 ❌ (") + strerror(errno) + ")\n";
    }

    result += "橋接狀態：" + get_status();
    return result;
}

static jstring to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_switchprokeyfix_BridgeNative_diagnostics(JNIEnv* env, jobject) {
    return to_jstring(env, diagnostics_text());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_switchprokeyfix_BridgeNative_startBridge(JNIEnv* env, jobject) {
    if (g_running.load()) {
        return to_jstring(env, get_status());
    }

    if (g_thread.joinable()) g_thread.join();
    g_running = true;
    set_status("正在啟動橋接…");
    g_thread = std::thread(bridge_loop);

    // Give the worker a short chance to detect immediate permission/device failures.
    usleep(250000);
    return to_jstring(env, get_status());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_switchprokeyfix_BridgeNative_stopBridge(JNIEnv* env, jobject) {
    g_running = false;
    if (g_thread.joinable()) g_thread.join();
    return to_jstring(env, get_status());
}
