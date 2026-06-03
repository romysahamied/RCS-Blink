package com.vernu.sms.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class RCSComposerHelper {

    public static void openComposer(
            Context context,
            String phone,
            String message
    ) {

        Intent intent = new Intent(Intent.ACTION_VIEW);

        intent.setData(Uri.parse("sms:" + phone));

        intent.putExtra("sms_body", message);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }
}