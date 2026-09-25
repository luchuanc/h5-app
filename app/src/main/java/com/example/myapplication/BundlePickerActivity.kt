package com.example.myapplication

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.myapplication.h5.H5PackageManager
import com.example.myapplication.h5.LaunchBundleOption

class BundlePickerActivity : ComponentActivity() {
    private lateinit var packageManager: H5PackageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager = H5PackageManager(applicationContext)

        render()
        if (packageManager.gameCatalog.url().isNotBlank()) refreshGames()
    }

    private fun refreshGames() {
        packageManager.refreshGamesAsync(
            onSuccess = { count -> runOnUiThread { if (!isDestroyed) { render(); Toast.makeText(this, "已更新 $count 个游戏", Toast.LENGTH_SHORT).show() } } },
            onFailure = { error -> runOnUiThread { if (!isDestroyed) Toast.makeText(this, "${error.message}，保留已缓存列表", Toast.LENGTH_LONG).show() } }
        )
    }

    private fun render() {
        val currentId = packageManager.getSelectedLaunchTargetId()
        val options = packageManager.getAvailableBundleOptions()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(Color.parseColor("#F4F7FB"))
        }

        content.addView(TextView(this).apply {
            text = "选择游戏 / Bundle"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#17324D"))
        })

        content.addView(TextView(this).apply {
            text = "选择后立即打开，下次启动仍使用所选游戏。可刷新平台最新列表。"
            textSize = 15f
            setTextColor(Color.parseColor("#5F7488"))
            setPadding(0, dp(10), 0, dp(22))
        })

        val catalogInput = EditText(this).apply {
            hint = "http://服务器:8200/api/catalog"
            setText(packageManager.gameCatalog.url())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            contentDescription = "游戏列表地址"
        }
        content.addView(catalogInput)
        content.addView(Button(this).apply {
            text = "刷新远端游戏列表"
            setOnClickListener {
                if (packageManager.gameCatalog.saveUrl(catalogInput.text.toString())) refreshGames()
                else Toast.makeText(this@BundlePickerActivity, "请输入有效 HTTP/HTTPS 列表地址", Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(createCustomUrlCard())

        options.forEach { option ->
            content.addView(createOptionCard(option, option.id == currentId))
        }

        val scrollView = ScrollView(this).apply {
            addView(content)
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scrollView)
    }

    private fun createCustomUrlCard(): LinearLayout {
        val input = EditText(this).apply {
            hint = "https://example.com/"
            setText(packageManager.getCustomRemoteUrl())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.parseColor("#17324D"))
            setHintTextColor(Color.parseColor("#8EA0B1"))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#C9D7E4"))
            }
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        }

        val button = Button(this).apply {
            text = "打开这个网址"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#0D6C91"))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            params.topMargin = dp(12)
            layoutParams = params
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(dp(1), Color.parseColor("#B7D6E5"))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(14)
            layoutParams = params

            addView(TextView(context).apply {
                text = "自定义 H5 页面"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#17324D"))
            })

            addView(TextView(context).apply {
                text = "输入网址后会按远程 bundle 打开，并记住为下次默认首页。"
                textSize = 14f
                setTextColor(Color.parseColor("#5F7488"))
                setPadding(0, dp(8), 0, dp(12))
            })

            addView(input)
            addView(button)

            button.setOnClickListener {
                val stored = packageManager.saveCustomRemoteUrl(input.text?.toString().orEmpty())
                if (!stored) {
                    Toast.makeText(context, "请输入有效的 http/https 网址", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Toast.makeText(context, "正在打开自定义 H5 页面", Toast.LENGTH_SHORT).show()
                H5PackageManager.restartApp(this@BundlePickerActivity)
            }
        }
    }

    private fun createOptionCard(option: LaunchBundleOption, selected: Boolean): LinearLayout {
        val borderColor = if (selected) "#0D6C91" else "#D6E0EA"
        val fillColor = if (selected) "#EAF7FC" else "#FFFFFF"

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor(fillColor))
                setStroke(dp(1), Color.parseColor(borderColor))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(14)
            layoutParams = params

            addView(TextView(context).apply {
                text = option.title + if (selected) "  •  Current" else ""
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#17324D"))
            })

            addView(TextView(context).apply {
                text = option.description
                textSize = 14f
                setTextColor(Color.parseColor("#5F7488"))
                setPadding(0, dp(8), 0, dp(10))
            })

            addView(TextView(context).apply {
                text = "Version ${option.version} · ${option.mode}"
                textSize = 13f
                setTextColor(Color.parseColor("#0D6C91"))
            })

            addView(TextView(context).apply {
                text = option.source
                textSize = 12f
                setTextColor(Color.parseColor("#7F93A5"))
                setPadding(0, dp(6), 0, 0)
            })

            setOnClickListener {
                val stored = packageManager.selectLaunchTarget(option.id)
                if (!stored) {
                    Toast.makeText(context, "Failed to switch bundle", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Toast.makeText(
                    context,
                    "Switching to ${option.title}",
                    Toast.LENGTH_SHORT
                ).show()
                H5PackageManager.restartApp(this@BundlePickerActivity)
            }
        }
    }

    override fun onDestroy() {
        packageManager.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
