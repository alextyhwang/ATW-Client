#ifndef ATW_NATIVEINSPECTION_H
#define ATW_NATIVEINSPECTION_H

#include <QString>

namespace NativeInspection {
struct Platform {
    QString os;
    QString cpu;
};

// Process architecture, never inferred from an archive or executable name.
Platform currentPlatform();
bool inspectMacJava17(const QString& executable, const Platform& platform, QString* error);

// Bounded classic ZIP reader: stored/deflated regular files only, no ZIP64,
// encryption, links or unsafe paths. Content identity includes the extraction
// policy, platform and game version. Cache reuse rechecks every file's bytes
// and the recognized Mac audio helper's executable permission.
bool prepareNativeArchive(const QString& archive, const QString& packageRoot,
                          const Platform& platform, const QString& version,
                          QString* directory, QString* error);
}

#endif
