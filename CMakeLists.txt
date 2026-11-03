cmake_minimum_required(VERSION 3.21)
if(WIN32)
    project(atw-client LANGUAGES CXX RC)
else()
    project(atw-client LANGUAGES CXX)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED TRUE)

set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

option(INCLUDE_UPDATER "Include update checker" OFF)
option(ATW_BUILD_LEGACY_TARGETS "Build legacy atw-client and atw-test-exe targets" OFF)

# Never consume copied Windows payloads as Mac artifacts. Use a fresh build
# directory and explicitly supply the intended Mac Java/Lunar distributions.
if(APPLE)
    set(ATW_JAVA_RUNTIME_SOURCE "" CACHE PATH "Mac Java 17 home (contains bin/java and release)")
    set(ATW_LUNAR_DATA_SOURCE "" CACHE PATH "Mac Lunar data root (contains offline/multiver)")
    if(NOT EXISTS "${ATW_JAVA_RUNTIME_SOURCE}/bin/java" OR
       NOT EXISTS "${ATW_JAVA_RUNTIME_SOURCE}/release")
        message(FATAL_ERROR "Set ATW_JAVA_RUNTIME_SOURCE to a Mac Java 17 home; copied Windows Java is not supported")
    endif()
    file(READ "${ATW_JAVA_RUNTIME_SOURCE}/release" ATW_JAVA_RELEASE)
    if(NOT ATW_JAVA_RELEASE MATCHES "JAVA_VERSION=\"17[.\"]" OR
       NOT ATW_JAVA_RELEASE MATCHES "OS_NAME=\"(Darwin|Mac OS X|macOS)\"")
        message(FATAL_ERROR "ATW_JAVA_RUNTIME_SOURCE must describe macOS Java 17")
    endif()
    if(NOT EXISTS "${ATW_LUNAR_DATA_SOURCE}/offline/multiver")
        message(FATAL_ERROR "Set ATW_LUNAR_DATA_SOURCE to the actual Mac Lunar distribution; runtime admission remains required")
    endif()
else()
    set(ATW_JAVA_RUNTIME_SOURCE "${CMAKE_CURRENT_LIST_DIR}/runtime/java" CACHE PATH "Bundled Java runtime source")
    set(ATW_LUNAR_DATA_SOURCE "${CMAKE_CURRENT_LIST_DIR}/data/lunarclient" CACHE PATH "Bundled Lunar data source")
endif()

set(QT_COMPONENTS Core Gui Widgets Svg Network)


find_package(Qt6 COMPONENTS
        ${QT_COMPONENTS}
        )
if (NOT Qt6_FOUND)
    find_package(Qt5 COMPONENTS
            ${QT_COMPONENTS}
            REQUIRED
            )
    set(QT_VER Qt5)
else()
set(QT_VER Qt6)
endif()

find_program(JAR_EXECUTABLE jar)
find_package(ZLIB REQUIRED)

# ---------------------------- Get current version and tag --------------------------------
set(LCQT_VERSION 0.0.1)
set(LCQT_VERSION_TAG v0.0.1)

# Version is set above; git tags can override if desired
if(Git_FOUND)
    execute_process(
            COMMAND ${GIT_EXECUTABLE} describe --tags --match "v[0-9]*"
            OUTPUT_VARIABLE tag
            OUTPUT_STRIP_TRAILING_WHITESPACE
            WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
    )
    if(tag AND tag MATCHES "^v([0-9]+)\\.([0-9]+)\\.([0-9]+)")
        set(LCQT_VERSION ${CMAKE_MATCH_1}.${CMAKE_MATCH_2}.${CMAKE_MATCH_3})
        if(tag MATCHES "^v([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)")
            set(LCQT_VERSION ${CMAKE_MATCH_1}.${CMAKE_MATCH_2}.${CMAKE_MATCH_3}.${CMAKE_MATCH_4})
        endif()
        set(LCQT_VERSION_TAG v${LCQT_VERSION})
        message(STATUS "Version from git tag: ${LCQT_VERSION_TAG}")
    endif()
endif()
message(STATUS "ATW Client version: ${LCQT_VERSION_TAG}")

# ----------------------- Parse json config file -----------------------------

file(READ ${CMAKE_CURRENT_LIST_DIR}/config.json CONFIG)

macro(CONFIG_VAR KEY OUTPUT_VAR)
    string(JSON ${OUTPUT_VAR} GET ${CONFIG} ${KEY})
endmacro()

config_var("agentsDestDir" AGENTS_DEST_DIR)
config_var("libsDestDir" LIBS_DEST_DIR)
config_var("githubReleaseApiLink" GITHUB_RELEASE_LINK)

# ----------------------- Replace values in cpp file -----------------------------

configure_file(${CMAKE_SOURCE_DIR}/buildconfig.cpp.in ${CMAKE_BINARY_DIR}/buildconfig.cpp @ONLY)
set(VERSION_FILE ${CMAKE_BINARY_DIR}/buildconfig.cpp)

set_source_files_properties(${VERSION_FILE} PROPERTIES COMPILE_DEFINITIONS "QSL=QStringLiteral")

# ----------------------- Sources ------------------------

set(ATW_CORE_SOURCES
        src/launch/offlinelauncher.cpp
        src/launch/offlinelauncher.h
        src/launch/launcher.h
        src/launch/launcher.cpp
        src/config/config.cpp
        src/config/config.h
        src/buildconfig.h
        src/util/fs.cpp
        src/util/fs.h
        src/util/utils.cpp
        src/util/utils.h
        src/util/nativeinspection.cpp
        src/util/nativeinspection.h
        src/core/agents/javaagents.cpp
        src/core/agents/javaagents.h
        src/core/mods/weavemods.cpp
        src/core/mods/weavemods.h
        src/auth/msaauth.cpp
        src/auth/msaauth.h
        src/auth/accountstore.cpp
        src/auth/accountstore.h
)

set(ATW_AUTH_UI_SOURCES
        src/auth/signindialog.cpp
        src/auth/signindialog.h
)

set(ATW_WIDGET_UI_SOURCES
        src/gui/mainwindow.cpp
        src/gui/mainwindow.h
        src/gui/pages/logspage.cpp
        src/gui/pages/logspage.h
        src/gui/pages/gamepage.cpp
        src/gui/pages/gamepage.h
        src/gui/pages/configurationpage.h
        src/gui/pages/agentspage.cpp
        src/gui/pages/agentspage.h
        src/gui/pages/modspage.cpp
        src/gui/pages/modspage.h
        src/gui/pages/helperspage.cpp
        src/gui/pages/helperspage.h
        src/gui/pages/otherpage.cpp
        src/gui/pages/otherpage.h
        src/gui/pages/packspage.cpp
        src/gui/pages/packspage.h
        src/gui/widgets/filechooser.cpp
        src/gui/widgets/filechooser.h
        src/gui/widgets/customtitlebar.cpp
        src/gui/widgets/customtitlebar.h
        src/gui/widgets/customtabbar.cpp
        src/gui/widgets/customtabbar.h
        src/gui/widgets/customtabwidget.cpp
        src/gui/widgets/customtabwidget.h
        src/gui/widgets/widgetutils.cpp
        src/gui/widgets/widgetutils.h
        src/buildconfig.h
        src/gui/agents/agentsmodel.cpp
        src/gui/agents/agentsmodel.h
        src/gui/agents/agentsview.cpp
        src/gui/agents/agentsview.h
        src/gui/mods/modsmodel.cpp
        src/gui/mods/modsmodel.h
        src/gui/mods/modsview.cpp
        src/gui/mods/modsview.h
        src/gui/style/removeoutline.cpp
        src/gui/style/removeoutline.h
        src/gui/widgets/accountwidget.cpp
        src/gui/widgets/accountwidget.h
        ${ATW_AUTH_UI_SOURCES}
)

set(UPDATER_SOURCES src/updater/updatechecker.cpp src/updater/updatechecker.h)

if(INCLUDE_UPDATER)
    list(APPEND ATW_WIDGET_UI_SOURCES ${UPDATER_SOURCES})
endif()

# ------------------------- Dependencies --------------------------

set(QT_COMPONENTS Core Gui Widgets Svg Network)

if(INCLUDE_UPDATER)
    list(APPEND QT_COMPONENTS Network)
endif()

find_package(${QT_VER} COMPONENTS ${QT_COMPONENTS} REQUIRED)


# ----------------------------- Core Boundary --------------------------------------

# This is intentionally an INTERFACE target for now. Core files still use
# per-executable compile definitions such as ATW_TEST_PORTABLE, so compiling
# them once into a static library would change behavior. This target gives the
# project a real ownership boundary while preserving current launch semantics.
add_library(atw_core INTERFACE)
target_sources(atw_core INTERFACE ${ATW_CORE_SOURCES} ${VERSION_FILE})
target_include_directories(atw_core INTERFACE src ${CMAKE_BINARY_DIR})
if(WIN32)
    # DPAPI (CryptProtectData) encrypts the saved Microsoft sign-in.
    target_link_libraries(atw_core INTERFACE crypt32)
endif()

# ----------------------------- Executable --------------------------------------

add_executable(atw-config WIN32 MACOSX_BUNDLE src/apps/config_main.cpp ${ATW_WIDGET_UI_SOURCES} res.qrc)
add_executable(atw-launch WIN32 MACOSX_BUNDLE src/apps/launch_main.cpp
        src/auth/accountgate.cpp src/auth/accountgate.h
        src/auth/onlinereadiness.cpp src/auth/onlinereadiness.h ${ATW_AUTH_UI_SOURCES} res.qrc)
target_compile_definitions(atw-config PRIVATE ATW_CONFIG_ONLY ATW_PACKAGE_MODE)
target_compile_definitions(atw-launch PRIVATE ATW_PACKAGE_MODE)

set(ATW_TARGETS atw-config atw-launch)

if(APPLE)
    # FS::getPackageRoot resolves the parent of each .app to this shared root.
    # Generator expression prevents multi-config generators adding Debug/Release.
    set_target_properties(atw-config atw-launch PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "$<1:${CMAKE_BINARY_DIR}>"
        MACOSX_BUNDLE_BUNDLE_VERSION "${LCQT_VERSION}"
        MACOSX_BUNDLE_SHORT_VERSION_STRING "${LCQT_VERSION}")
    set_target_properties(atw-config PROPERTIES MACOSX_BUNDLE_GUI_IDENTIFIER "local.atw.config")
    set_target_properties(atw-launch PROPERTIES MACOSX_BUNDLE_GUI_IDENTIFIER "local.atw.launch")
endif()

if(ATW_BUILD_LEGACY_TARGETS)
    add_executable(atw-client WIN32 MACOSX_BUNDLE src/main.cpp ${ATW_WIDGET_UI_SOURCES} res.qrc)
    add_executable(atw-test-exe WIN32 MACOSX_BUNDLE src/main.cpp ${ATW_WIDGET_UI_SOURCES} res.qrc)
    target_compile_definitions(atw-test-exe PRIVATE ATW_TEST_PORTABLE)
    list(APPEND ATW_TARGETS atw-client atw-test-exe)
endif()

# ----------------------------- Compile Definitions --------------------------------

if(INCLUDE_UPDATER)
    foreach(ATW_TARGET IN LISTS ATW_TARGETS)
        target_compile_definitions(${ATW_TARGET} PRIVATE INCLUDE_UPDATER)
    endforeach()
endif()

# ------------------------- Copy java agents and libraries -----------------------

set(JAR_DEST_DIR ${CMAKE_BINARY_DIR})
set(ATW_JAVA_AGENTS_SOURCE "${CMAKE_CURRENT_LIST_DIR}/java/agents" CACHE PATH "Selected Java agent source directory")
set(ATW_JAVA_LIBS_SOURCE "${CMAKE_CURRENT_LIST_DIR}/java/libs" CACHE PATH "Selected Java library source directory")

macro(COPY_JARS S_DIR T_DIR NEW_DIR)
    add_custom_command(
            OUTPUT "${JAR_DEST_DIR}/${T_DIR}"
            COMMAND ${CMAKE_COMMAND}
            -Dsrc_dir=${S_DIR}
            -Ddst_dir=${JAR_DEST_DIR}/${T_DIR}
            -P ${CMAKE_CURRENT_LIST_DIR}/cmake/copy_jars.cmake
            COMMENT "Copying '${S_DIR}' to '${T_DIR}'"
            VERBATIM
    )
    set(${NEW_DIR} "${JAR_DEST_DIR}/${T_DIR}")
endmacro()

copy_jars("${ATW_JAVA_AGENTS_SOURCE}" ${AGENTS_DEST_DIR} COPIED_AGENTS)
copy_jars("${ATW_JAVA_LIBS_SOURCE}" ${LIBS_DEST_DIR} COPIED_LIBRARIES)

add_custom_target(CopyJars ALL DEPENDS ${COPIED_AGENTS} ${COPIED_LIBRARIES})
foreach(ATW_TARGET IN LISTS ATW_TARGETS)
    add_dependencies(${ATW_TARGET} CopyJars)
endforeach()

# ------------------------- Copy bundled Weave mods -----------------------

set(ATW_WEAVE_MODS_SOURCE "${CMAKE_CURRENT_LIST_DIR}/weave-mods/runtime" CACHE PATH "Explicitly selected Weave mod payloads")
set(BUNDLED_WEAVE_MODS_SOURCE "${ATW_WEAVE_MODS_SOURCE}")
set(BUNDLED_WEAVE_MODS_DEST ${CMAKE_BINARY_DIR}/data/home/.weave/mods)
# Port builds are explicit: install verified outputs before configuring CMake.
# This avoids silently rebuilding an obsolete 0.2.6 artifact during launcher builds.
add_custom_target(CopyBundledWeaveMods ALL
    COMMAND ${CMAKE_COMMAND} -E make_directory "${BUNDLED_WEAVE_MODS_DEST}"
    COMMAND ${CMAKE_COMMAND} -E copy_directory "${BUNDLED_WEAVE_MODS_SOURCE}" "${BUNDLED_WEAVE_MODS_DEST}"
    COMMENT "Copying verified Minecraft 1.8.9 Weave 1.4.1 mods"
    VERBATIM
)
set(ATW_WEAVE_RUNTIME_SOURCE "${CMAKE_CURRENT_LIST_DIR}/runtime/weave" CACHE PATH "Pinned Weave runtime and private API source")
set(ATW_WEAVE_RUNTIME_DEST "${CMAKE_BINARY_DIR}/runtime/weave")
foreach(WEAVE_FILE IN ITEMS
        "Weave-Loader-Agent-1.4.1.jar" "vanilla-1.8.9.jar"
        "maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar")
    if(NOT EXISTS "${ATW_WEAVE_RUNTIME_SOURCE}/${WEAVE_FILE}")
        message(FATAL_ERROR "Install the pinned Weave runtime before building: ${WEAVE_FILE}")
    endif()
endforeach()
add_custom_target(CopyBundledWeaveRuntime ALL
    COMMAND ${CMAKE_COMMAND} -E make_directory "${ATW_WEAVE_RUNTIME_DEST}"
    COMMAND ${CMAKE_COMMAND} -E copy_directory "${ATW_WEAVE_RUNTIME_SOURCE}" "${ATW_WEAVE_RUNTIME_DEST}"
    COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_BINARY_DIR}/data/home/.weave/.maven-repository"
    COMMAND ${CMAKE_COMMAND} -E copy_directory "${ATW_WEAVE_RUNTIME_SOURCE}/maven-repository"
        "${CMAKE_BINARY_DIR}/data/home/.weave/.maven-repository"
    COMMENT "Copying pinned Weave agent, vanilla jar, and private offline API repository"
    VERBATIM
)
foreach(ATW_TARGET IN LISTS ATW_TARGETS)
    add_dependencies(${ATW_TARGET} CopyBundledWeaveMods CopyBundledWeaveRuntime)
endforeach()

# ------------------------- Copy bundled Java runtime -----------------------

set(BUNDLED_JAVA_RUNTIME_SOURCE "${ATW_JAVA_RUNTIME_SOURCE}")
set(BUNDLED_JAVA_RUNTIME_DEST ${CMAKE_BINARY_DIR}/runtime/java)

add_custom_target(CopyBundledJavaRuntime ALL
    COMMAND ${CMAKE_COMMAND} -E make_directory "${BUNDLED_JAVA_RUNTIME_DEST}"
    COMMAND ${CMAKE_COMMAND} -E copy_directory "${BUNDLED_JAVA_RUNTIME_SOURCE}" "${BUNDLED_JAVA_RUNTIME_DEST}"
    COMMENT "Copying bundled Java runtime"
    VERBATIM
)

foreach(ATW_TARGET IN LISTS ATW_TARGETS)
    add_dependencies(${ATW_TARGET} CopyBundledJavaRuntime)
endforeach()

# ------------------------- Copy bundled Lunar Client data -----------------

set(BUNDLED_LUNAR_DATA_SOURCE "${ATW_LUNAR_DATA_SOURCE}")
set(BUNDLED_LUNAR_DATA_DEST ${CMAKE_BINARY_DIR}/data/home/.lunarclient)
set(ATW_LUNAR_BRANDING_OVERLAY ${CMAKE_CURRENT_LIST_DIR}/res/lunar-overrides)
set(ATW_LUNAR_BRANDING_TARGET_JAR ${BUNDLED_LUNAR_DATA_DEST}/offline/multiver/lunar.jar)

if(EXISTS "${BUNDLED_LUNAR_DATA_SOURCE}")
    add_custom_target(CopyBundledLunarData ALL
        COMMAND ${CMAKE_COMMAND} -E make_directory "${BUNDLED_LUNAR_DATA_DEST}"
        COMMAND ${CMAKE_COMMAND}
            -DSOURCE=${BUNDLED_LUNAR_DATA_SOURCE}
            -DDESTINATION=${BUNDLED_LUNAR_DATA_DEST}
            -P "${CMAKE_CURRENT_LIST_DIR}/cmake/copy_lunar_runtime.cmake"
        COMMENT "Copying bundled Lunar Client data"
        VERBATIM
    )

    if(JAR_EXECUTABLE)
        add_custom_target(PatchBundledLunarBranding ALL
            COMMAND ${CMAKE_COMMAND}
                -DJAR_EXECUTABLE=${JAR_EXECUTABLE}
                -DLUNAR_JAR=${ATW_LUNAR_BRANDING_TARGET_JAR}
                -DOVERLAY_DIR=${ATW_LUNAR_BRANDING_OVERLAY}
                -P "${CMAKE_CURRENT_LIST_DIR}/cmake/patch_lunar_branding.cmake"
            DEPENDS CopyBundledLunarData
            COMMENT "Patching bundled Lunar branding resources"
            VERBATIM
        )

        add_dependencies(atw-config PatchBundledLunarBranding)
        add_dependencies(atw-launch PatchBundledLunarBranding)
    else()
        message(WARNING "jar executable not found; bundled Lunar branding resources will not be patched")
    endif()

    add_dependencies(atw-config CopyBundledLunarData)
    add_dependencies(atw-launch CopyBundledLunarData)
endif()

# Deploy both app bundles; they share data/runtime, but each needs Qt frameworks
# and the Cocoa platform plugin inside its own bundle. Signing/notarization is a
# separate distribution step and is not asserted by deployment.
if(APPLE)
    get_target_property(ATW_QMAKE_EXECUTABLE ${QT_VER}::qmake IMPORTED_LOCATION)
    get_filename_component(ATW_QT_BIN_DIR "${ATW_QMAKE_EXECUTABLE}" DIRECTORY)
    find_program(ATW_MACDEPLOYQT_EXECUTABLE macdeployqt HINTS "${ATW_QT_BIN_DIR}" NO_DEFAULT_PATH)
    if(NOT ATW_MACDEPLOYQT_EXECUTABLE)
        message(FATAL_ERROR "macdeployqt from the selected Qt installation is required")
    endif()
    if(QT_VER STREQUAL "Qt6")
        execute_process(COMMAND "${ATW_QMAKE_EXECUTABLE}" -query QT_INSTALL_PLUGINS
            OUTPUT_VARIABLE ATW_QT_PLUGIN_DIR OUTPUT_STRIP_TRAILING_WHITESPACE
            COMMAND_ERROR_IS_FATAL ANY)
        set(ATW_MAC_TLS_PLUGIN "${ATW_QT_PLUGIN_DIR}/tls/libqsecuretransportbackend.dylib")
        if(NOT EXISTS "${ATW_MAC_TLS_PLUGIN}")
            message(FATAL_ERROR "Qt Secure Transport TLS plugin is required for Microsoft sign-in")
        endif()
    endif()
    foreach(ATW_TARGET IN LISTS ATW_TARGETS)
        # macdeployqt can omit dynamically discovered TLS backends. Seed the
        # native Apple backend explicitly before dependency deployment/signing.
        if(QT_VER STREQUAL "Qt6")
            add_custom_command(TARGET ${ATW_TARGET} POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E make_directory
                    "$<TARGET_BUNDLE_DIR:${ATW_TARGET}>/Contents/PlugIns/tls"
                COMMAND ${CMAKE_COMMAND} -E copy_if_different "${ATW_MAC_TLS_PLUGIN}"
                    "$<TARGET_BUNDLE_DIR:${ATW_TARGET}>/Contents/PlugIns/tls/"
                VERBATIM)
        endif()
        add_custom_command(TARGET ${ATW_TARGET} POST_BUILD
            COMMAND "${ATW_MACDEPLOYQT_EXECUTABLE}" "$<TARGET_BUNDLE_DIR:${ATW_TARGET}>" -always-overwrite
            COMMENT "Deploying Qt frameworks and Cocoa plugin for ${ATW_TARGET}"
            VERBATIM)
    endforeach()
endif()

# ----------------------- Copy application icon ------------------------------

configure_file(${CMAKE_SOURCE_DIR}/res/icon.ico ${CMAKE_BINARY_DIR}/icon.ico COPYONLY)
configure_file(${CMAKE_SOURCE_DIR}/res/icon-config.ico ${CMAKE_BINARY_DIR}/icon-config.ico COPYONLY)
configure_file(${CMAKE_SOURCE_DIR}/res/icon-launch.ico ${CMAKE_BINARY_DIR}/icon-launch.ico COPYONLY)
configure_file(${CMAKE_SOURCE_DIR}/res/minecraft.ico ${CMAKE_BINARY_DIR}/minecraft.ico COPYONLY)

# Embed icon in Windows executable (for taskbar pin, Explorer, etc.)
if(WIN32)
    target_sources(atw-config PRIVATE ${CMAKE_SOURCE_DIR}/res/atw-config.rc)
    target_sources(atw-launch PRIVATE ${CMAKE_SOURCE_DIR}/res/atw-launch.rc)
    if(ATW_BUILD_LEGACY_TARGETS)
        target_sources(atw-client PRIVATE ${CMAKE_SOURCE_DIR}/res/atw-launch.rc)
        target_sources(atw-test-exe PRIVATE ${CMAKE_SOURCE_DIR}/res/atw-client.rc)
    endif()
endif()

# ----------------------- Link against libraries ------------------------------

list(TRANSFORM QT_COMPONENTS PREPEND ${QT_VER}:: OUTPUT_VARIABLE QT_LIBS)

foreach(ATW_TARGET IN LISTS ATW_TARGETS)
    target_link_libraries(${ATW_TARGET} atw_core ${QT_LIBS} ZLIB::ZLIB)
endforeach()

if(APPLE)
    find_library(ATW_SECURITY_FRAMEWORK Security REQUIRED)
    foreach(ATW_TARGET IN LISTS ATW_TARGETS)
        target_link_libraries(${ATW_TARGET} "${ATW_SECURITY_FRAMEWORK}")
    endforeach()
endif()

# ---------------------- Set include directory ---------------------------

foreach(ATW_TARGET IN LISTS ATW_TARGETS)
    target_include_directories(${ATW_TARGET} PRIVATE src)
endforeach()

# ---------------------- Deploy Qt DLLs (Windows) ---------------------------

if(WIN32 AND QT_VER STREQUAL "Qt6")
    get_filename_component(QT_BIN_DIR "${Qt6_DIR}/../../../bin" ABSOLUTE)
    foreach(ATW_TARGET IN LISTS ATW_TARGETS)
        add_custom_command(TARGET ${ATW_TARGET} POST_BUILD
            COMMAND "${QT_BIN_DIR}/windeployqt.exe" --no-translations "$<TARGET_FILE:${ATW_TARGET}>"
            COMMENT "Deploying Qt DLLs for ${ATW_TARGET} with windeployqt..."
        )
    endforeach()
elseif(WIN32 AND QT_VER STREQUAL "Qt5")
    get_filename_component(QT_BIN_DIR "${Qt5_DIR}/../../../bin" ABSOLUTE)
    foreach(ATW_TARGET IN LISTS ATW_TARGETS)
        add_custom_command(TARGET ${ATW_TARGET} POST_BUILD
            COMMAND "${QT_BIN_DIR}/windeployqt.exe" --no-translations "$<TARGET_FILE:${ATW_TARGET}>"
            COMMENT "Deploying Qt DLLs for ${ATW_TARGET} with windeployqt..."
        )
    endforeach()
endif()

if(WIN32)
    # windeployqt does not deploy independently linked zlib. Inspect the actual
    # executable imports after Qt deployment, so both static and shared zlib
    # builds work without relying on zlib being installed on the destination PC.
    set(ATW_RUNTIME_DEPENDENCY_DIRS "" CACHE STRING "Additional Windows DLL search directories (semicolon-separated)")
    if(MSVC)
        get_filename_component(ATW_LINKER_DIR "${CMAKE_LINKER}" DIRECTORY)
        find_program(ATW_DEPENDENCY_READER dumpbin HINTS "${ATW_LINKER_DIR}" REQUIRED)
        set(ATW_DEPENDENCY_TOOL dumpbin)
    else()
        set(ATW_DEPENDENCY_READER "${CMAKE_OBJDUMP}")
        set(ATW_DEPENDENCY_TOOL objdump)
        if(NOT EXISTS "${ATW_DEPENDENCY_READER}")
            message(FATAL_ERROR "A matching objdump is required for Windows runtime deployment")
        endif()
    endif()
    foreach(ATW_TARGET IN LISTS ATW_TARGETS)
        add_custom_command(TARGET ${ATW_TARGET} POST_BUILD
            COMMAND "${CMAKE_COMMAND}"
                "-DATW_EXECUTABLE=$<TARGET_FILE:${ATW_TARGET}>"
                "-DATW_ZLIB_DIR=$<TARGET_FILE_DIR:ZLIB::ZLIB>"
                "-DATW_QT_DIR=$<TARGET_FILE_DIR:${QT_VER}::Core>"
                "-DATW_EXTRA_DIRS=${ATW_RUNTIME_DEPENDENCY_DIRS}"
                "-DCMAKE_GET_RUNTIME_DEPENDENCIES_PLATFORM=windows+pe"
                "-DCMAKE_GET_RUNTIME_DEPENDENCIES_TOOL=${ATW_DEPENDENCY_TOOL}"
                "-DCMAKE_GET_RUNTIME_DEPENDENCIES_COMMAND=${ATW_DEPENDENCY_READER}"
                -P "${CMAKE_CURRENT_LIST_DIR}/cmake/deploy_windows_runtime.cmake"
            COMMENT "Deploying imported Windows runtime DLLs for ${ATW_TARGET}"
            VERBATIM)
    endforeach()
endif()
