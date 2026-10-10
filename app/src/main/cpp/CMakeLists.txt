# Giac (GPL-3.0, Bernard Parisse, Université Grenoble Alpes) from GeoGebra's
# mirror, built from its pinned source (fetched and checked by the app's
# build) with GeoGebra's Android settings and the GMP and MPFR (LGPL-3.0)
# it ships prebuilt; then a two-call bridge for the isolated GiacService.
cmake_minimum_required(VERSION 3.22)
project(calcgiac CXX C)

set(G ${GIAC_SRC})
if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(HEADERS arm-v8)
  set(PREBUILT aarch64)
elseif(ANDROID_ABI STREQUAL "x86_64")
  set(HEADERS x86-64)
  set(PREBUILT x86-64)
else()
  message(FATAL_ERROR "Giac: no GMP and MPFR for ${ANDROID_ABI}")
endif()

file(GLOB GIAC_SOURCES ${G}/src/giac/cpp/*.cc)
add_library(calcgiac SHARED ${GIAC_SOURCES} giac_jni.cpp)
target_compile_definitions(calcgiac PRIVATE IN_GIAC GIAC_GENERIC_CONSTANTS HAVE_CONFIG_H GIAC_GGB TIMEOUT HAVE_UNISTD_H NO_BSD PIC SIZEOF_LONG=4 VERSION="2.0.0")
target_compile_options(calcgiac PRIVATE -O2 -fexceptions -frtti -fno-strict-aliasing -fvisibility=hidden -w -iquote ${G}/src/giac/headers/android/${HEADERS})
target_include_directories(calcgiac PRIVATE ${G}/src/giac/headers ${G}/src/giac/cpp)
target_link_libraries(calcgiac PRIVATE ${G}/src/jni/prebuilt/android/${PREBUILT}/libmpfr.a ${G}/src/jni/prebuilt/android/${PREBUILT}/libgmp.a log)
target_link_options(calcgiac PRIVATE -s -Wl,--gc-sections -Wl,--exclude-libs,ALL)
