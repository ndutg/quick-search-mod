file_path = "app/src/main/java/com/tk/quicksearch/search/data/AppsRepository.kt"

with open(file_path, "r") as f:
    content = f.read()

changes = 0

old_changed = "override fun onPackageChanged(packageName: String, user: UserHandle) = Unit"
new_changed = (
    "override fun onPackageChanged(packageName: String, user: UserHandle) =\n"
    "                    invalidate(AppCatalogChange.forPackage(packageName, user, isRemoval = false))"
)
if old_changed in content:
    content = content.replace(old_changed, new_changed)
    changes += 1
    print("Patched onPackageChanged")
elif "isRemoval = false))" in content and "onPackageChanged" in content:
    print("onPackageChanged appears already patched")
else:
    print("WARNING: onPackageChanged anchor not found")

old_avail = "override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit"
new_avail = (
    "override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =\n"
    "                    packageNames.forEach { invalidate(AppCatalogChange.forPackage(it, user, isRemoval = false)) }"
)
if old_avail in content:
    content = content.replace(old_avail, new_avail)
    changes += 1
    print("Patched onPackagesAvailable")
elif "packageNames.forEach { invalidate" in content:
    print("onPackagesAvailable appears already patched")
else:
    print("WARNING: onPackagesAvailable anchor not found")

with open(file_path, "w") as f:
    f.write(content)

print(f"\nTotal changes: {changes}")
