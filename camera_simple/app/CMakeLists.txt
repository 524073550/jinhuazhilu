
include_directories(src/main/jniLibs/include)

cmake_minimum_required(VERSION 3.4.1)


add_library(
        camera
        SHARED

        src/main/jni/com_ke_zhu_camera_simple_JniUtils.cpp
)

add_library(
        libyuv_static
        SHARED
        IMPORTED
)
set_target_properties(
        libyuv_static
        ROPERTIES IMPORTED_LOCATION
        src/main/jniLibs/arm64-v8a/libyuv_static.so
)

find_library(
        log-lib
        log)

target_link_libraries(
        camera
        libyuv_static
        ${log-lib})