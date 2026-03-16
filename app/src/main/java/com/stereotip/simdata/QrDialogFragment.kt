package com.stereotip.simdata

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class QrDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        val bitmap = requireArguments().getParcelable<Bitmap>(ARG_BITMAP)!!

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 18)
        }
        val tv = TextView(requireContext()).apply {
            text = title
            textSize = 20f
        }
        val iv = ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
        }
        container.addView(tv)
        container.addView(iv)

        return AlertDialog.Builder(requireContext())
            .setView(container)
            .setPositiveButton("סגור", null)
            .create()
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_BITMAP = "bitmap"
        fun newInstance(bitmap: Bitmap, title: String): QrDialogFragment {
            return QrDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putParcelable(ARG_BITMAP, bitmap)
                }
            }
        }
    }
}
