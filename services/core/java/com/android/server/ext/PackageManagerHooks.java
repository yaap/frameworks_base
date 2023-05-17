package com.android.server.ext;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppBindArgs;
import android.content.pm.GosPackageState;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.app.ContactScopes;
import com.android.server.pm.GosPackageStatePmHooks;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.parsing.pkg.AndroidPackage;
import com.android.server.pm.pkg.GosPackageStatePm;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.parsing.ParsingPackage;

public class PackageManagerHooks {

    public static void removeUsesPermissions(ParsingPackage pkg, String... perms) {
        var set = new ArraySet<>(perms);
        pkg.getRequestedPermissions().removeAll(set);
        pkg.getUsesPermissions().removeIf(p -> set.contains(p.getName()));
    }

    public static boolean shouldBlockGrantRuntimePermission(
            PackageManagerInternal pm, String permName, String packageName, int userId)
    {
        if (ContactScopes.getSpoofablePermissionDflag(permName) != 0) {
            GosPackageStatePm gosPs = pm.getGosPackageState(packageName, userId);
            if (gosPs != null && gosPs.hasFlags(GosPackageState.FLAG_CONTACT_SCOPES_ENABLED)) {
                String msg = "refusing to grant " + permName + " to " + packageName +
                        ": Contact Scopes is enabled";
                Slog.d("PermissionManager", msg);
                return true;
            }
        }

        return false;
    }

    @Nullable
    public static Bundle getExtraAppBindArgs(PackageManagerService pm, String packageName) {
        final int callingUid = Binder.getCallingUid();
        final int appId = UserHandle.getAppId(callingUid);
        final int userId = UserHandle.getUserId(callingUid);

        AndroidPackage pkg = pm.snapshotComputer().getPackage(packageName);
        if (pkg == null) {
            return null;
        }

        if (pkg.getUid() != appId) { // getUid() actually returns appId, it has a historic name
            return null;
        }

        GosPackageState gosPs = GosPackageStatePmHooks.get(pm, callingUid, packageName, userId);

        int[] flagsArr = new int[AppBindArgs.FLAGS_ARRAY_LEN];

        var b = new Bundle();
        b.putParcelable(AppBindArgs.KEY_GOS_PACKAGE_STATE, gosPs);
        b.putIntArray(AppBindArgs.KEY_FLAGS_ARRAY, flagsArr);

        return b;
    }
}
