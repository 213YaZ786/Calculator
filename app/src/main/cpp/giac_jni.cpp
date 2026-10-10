// The bridge between GiacService and Giac: an expression in, its value out,
// each in a fresh context; Giac's own messages go nowhere. interrupt() stops
// a long computation from another thread.
#include <jni.h>
#include <sstream>
#include <string>
#include "giac.h"

using namespace giac;

extern "C" JNIEXPORT jstring JNICALL
Java_com_yaz_calculator_engine_giac_GiacService_eval(JNIEnv* env, jclass, jstring input) {
    const char* chars = env->GetStringUTFChars(input, nullptr);
    std::string text(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(input, chars);
    ctrl_c = false;
    interrupted = false;
    std::string out;
    std::ostringstream silent;
    try {
        context ctx;
        logptr(&silent, &ctx);
        gen g(text, &ctx);
        out = eval(g, 1, &ctx).print(&ctx);
    } catch (std::exception& e) {
        out = std::string("GIAC_ERROR: ") + e.what();
    } catch (...) {
        out = "GIAC_ERROR";
    }
    if (interrupted || ctrl_c) out = "GIAC_ERROR: interrupted";
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_yaz_calculator_engine_giac_GiacService_interrupt(JNIEnv*, jclass) {
    ctrl_c = true;
    interrupted = true;
}
