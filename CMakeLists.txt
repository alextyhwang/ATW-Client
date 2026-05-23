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
        src/core/agents/javaagents.cpp
        src/core/agents/javaagents.h
        src/core/mods/weavemods.cpp
        src/core/mods/weavemods.h
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

# ----------------------------- Executable --------------------------------------

add_executable(atw-config WIN32 MACOSX_BUNDLE src/apps/config_main.cpp ${ATW_WIDGET_UI_SOURCES} res.qrc)
add_executable(atw-launch WIN32 MACOSX_BUNDLE src/apps/launch_main.cpp res.qrc)
target_compile_definitions(atw-config PRIVATE ATW_CONFIG_ONLY ATW_PACKAGE_MODE)
target_compile_definitions(atw-launch PRIVATE ATW_PACKAGE_MODE)

set(ATW_TARGETS atw-config atw-launch)

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

if(UNIX AND APPLE)
    set(JAR_DEST_DIR ${CMAKE_BINARY_DIR}/atw-config.app/Contents/MacOS)
else()
    set(JAR_DEST_DIR ${CMAKE_BINARY_DIR})
endif()

macro(COPY_JARS S_DIR T_DIR NEW_DIR)
    add_custom_command(
            OUTPUT "${JAR_DEST_DIR}/${T_DIR}"
            COMMAND ${CMAKE_COMMAND}
            -Dsrc_dir=${CMAKE_CURRENT_LIST_DIR}/java/${S_DIR}
            -Ddst_dir=${JAR_DEST_DIR}/${T_DIR}
            -P ${CMAKE_CURRENT_LIST_DIR}/cmake/copy_jars.cmake
            COMMENT "Copying '${S_DIR}' to '${T_DIR}'"
            VERBATIM
    )
    set(${NEW_DIR} "${JAR_DEST_DIR}/${T_DIR}")
endmacro()

copy_jars(agents ${AGENTS_DEST_DIR} COPIED_AGENTS)
copy_jars(libs ${LIBS_DEST_DIR} COPIED_LIBRARIES)

add_custom_target(CopyJars ALL DEPENDS ${COPIED_AGENTS} ${COPIED_LIBRARIES})
foreach(ATW_TARGET IN LISTS ATW_TARGETS)
    add_dependencies(${ATW_TARGET} CopyJars)
endforeach()

# ------------------------- Copy bundled Weave mods -----------------------

set(BUNDLED_WEAVE_MODS_SOURCE ${CMAKE_CURRENT_LIST_DIR}/weave-mods/runtime)
set(BUNDLED_WEAVE_MODS_DEST ${CMAKE_BINARY_DIR}/weave-mods)
set(ATW_LEVELHEAD_PROJECT_DIR ${CMAKE_CURRENT_LIST_DIR}/weave-mods/atw-levelhead)
set(ATW_LEVELHEAD_JAR ${ATW_LEVELHEAD_PROJECT_DIR}/build/libs/ATWLevelHead-0.1.0.jar)
set(ATW_LEVELHEAD_RUNTIME_JAR ${BUNDLED_WEAVE_MODS_SOURCE}/ATWLevelHead-0.1.0.jar)
set(ATW_REBRAND_PROJECT_DIR ${CMAKE_CURRENT_LIST_DIR}/weave-mods/atw-rebrand)
set(ATW_REBRAND_JAR ${ATW_REBRAND_PROJECT_DIR}/build/libs/ATWRebrand-0.1.0.jar)
set(ATW_REBRAND_RUNTIME_JAR ${BUNDLED_WEAVE_MODS_SOURCE}/ATWRebrand-0.1.0.jar)

add_custom_target(BuildATWLevelHead
    COMMAND cmd /c gradlew.bat build
    COMMAND ${CMAKE_COMMAND} -E make_directory "${BUNDLED_WEAVE_MODS_SOURCE}"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "${ATW_LEVELHEAD_JAR}" "${ATW_LEVELHEAD_RUNTIME_JAR}"
    WORKING_DIRECTORY "${ATW_LEVELHEAD_PROJECT_DIR}"
    COMMENT "Building bundled ATW LevelHead Weave mod"
    VERBATIM
)

add_custom_target(BuildATWRebrand
    COMMAND cmd /c gradlew.bat build
    COMMAND ${CMAKE_COMMAND} -E make_directory "${BUNDLED_WEAVE_MODS_SOURCE}"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "${ATW_REBRAND_JAR}" "${ATW_REBRAND_RUNTIME_JAR}"
    WORKING_DIRECTORY "${ATW_REBRAND_PROJECT_DIR}"
    COMMENT "Building bundled ATW Rebrand Weave mod"
    VERBATIM
)

add_custom_target(CopyBundledWeaveMods ALL
    COMMAND ${CMAKE_COMMAND} -E make_directory "${BUNDLED_WEAVE_MODS_DEST}"
    COMMAND ${CMAKE_COMMAND} -E copy_directory "${BUNDLED_WEAVE_MODS_SOURCE}" "${BUNDLED_WEAVE_MODS_DEST}"
    COMMENT "Copying bundled Weave mods to runtime folder"
    VERBATIM
)
add_dependencies(CopyBundledWeaveMods BuildATWLevelHead)
add_dependencies(CopyBundledWeaveMods BuildATWRebrand)

foreach(ATW_TARGET IN LISTS ATW_TARGETS)
    add_dependencies(${ATW_TARGET} CopyBundledWeaveMods)
endforeach()

# ------------------------- Copy bundled Java runtime -----------------------

set(BUNDLED_JAVA_RUNTIME_SOURCE ${CMAKE_CURRENT_LIST_DIR}/runtime/java)
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

set(BUNDLED_LUNAR_DATA_SOURCE ${CMAKE_CURRENT_LIST_DIR}/data/lunarclient)
set(BUNDLED_LUNAR_DATA_DEST ${CMAKE_BINARY_DIR}/data/lunarclient)

if(EXISTS "${BUNDLED_LUNAR_DATA_SOURCE}")
    add_custom_target(CopyBundledLunarData ALL
        COMMAND ${CMAKE_COMMAND} -E make_directory "${BUNDLED_LUNAR_DATA_DEST}"
        COMMAND ${CMAKE_COMMAND} -E copy_directory "${BUNDLED_LUNAR_DATA_SOURCE}" "${BUNDLED_LUNAR_DATA_DEST}"
        COMMENT "Copying bundled Lunar Client data"
        VERBATIM
    )

    add_dependencies(atw-config CopyBundledLunarData)
    add_dependencies(atw-launch CopyBundledLunarData)
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
    target_link_libraries(${ATW_TARGET} atw_core ${QT_LIBS})
endforeach()

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
