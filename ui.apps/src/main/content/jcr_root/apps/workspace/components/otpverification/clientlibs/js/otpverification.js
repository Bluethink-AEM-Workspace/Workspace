(function () {
    function initOtpComponent() {
        const container = document.getElementById("otpContainer");
        if (!container) return;

        const generateUrl = container.getAttribute("data-generate-url") || "/bin/workspace/generate-otp";
        const verifyUrl = container.getAttribute("data-verify-url") || "/bin/workspace/verify-otp";
        const expirySeconds = parseInt(container.getAttribute("data-expiry")) || 60;

        const stage1 = document.getElementById("otp-stage-1");
        const stage2 = document.getElementById("otp-stage-2");
        const msgBox = document.getElementById("otp-message");

        const generateBtn = document.getElementById("generate-otp-btn");
        const verifyBtn = document.getElementById("verify-otp-btn");
        const resendBtn = document.getElementById("resend-otp-btn");
        const timerDisplay = document.getElementById("otp-timer-display");

        let captchaCode1 = "";
        let captchaCode2 = "";
        let timerInterval = null;

        // Clean State Management via BEM Classes
        function showMessage(msg, type) {
            if (!msgBox) return;
            msgBox.textContent = msg;
            msgBox.className = "cmp-otp__message cmp-otp__message--" + type;
        }

        function clearMessage() {
            if (!msgBox) return;
            msgBox.textContent = "";
            msgBox.className = "cmp-otp__message cmp-otp__message--hidden";
        }

        // HTML5 Canvas API Drawing (Native Canvas 2D Context)
        function drawCaptcha(canvasId) {
            const canvas = document.getElementById(canvasId);
            if (!canvas) return "";
            const ctx = canvas.getContext("2d");
            const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            let code = "";

            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.fillStyle = "#f8fafc";
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            ctx.font = "bold 20px Arial";
            ctx.fillStyle = "#1e293b";
            for (let i = 0; i < 6; i++) {
                const char = chars.charAt(Math.floor(Math.random() * chars.length));
                code += char;
                ctx.save();
                ctx.translate(20 + i * 20, 26);
                ctx.rotate((Math.random() - 0.5) * 0.4);
                ctx.fillText(char, 0, 0);
                ctx.restore();
            }

            ctx.strokeStyle = "#cbd5e1";
            for (let i = 0; i < 3; i++) {
                ctx.beginPath();
                ctx.moveTo(Math.random() * canvas.width, Math.random() * canvas.height);
                ctx.lineTo(Math.random() * canvas.width, Math.random() * canvas.height);
                ctx.stroke();
            }

            return code;
        }

        captchaCode1 = drawCaptcha("captchaCanvasStage1");
        captchaCode2 = drawCaptcha("captchaCanvasStage2");

        document.getElementById("refreshCaptchaStage1")?.addEventListener("click", function () {
            captchaCode1 = drawCaptcha("captchaCanvasStage1");
        });
        document.getElementById("refreshCaptchaStage2")?.addEventListener("click", function () {
            captchaCode2 = drawCaptcha("captchaCanvasStage2");
        });

        function startTimer() {
            let timeLeft = expirySeconds;
            if (resendBtn) resendBtn.disabled = true;
            clearInterval(timerInterval);

            timerInterval = setInterval(() => {
                if (timeLeft <= 0) {
                    clearInterval(timerInterval);
                    if (timerDisplay) timerDisplay.textContent = "OTP Expired";
                    if (resendBtn) resendBtn.disabled = false;
                } else {
                    if (timerDisplay) timerDisplay.textContent = `Expires in ${timeLeft}s`;
                    timeLeft--;
                }
            }, 1000);
        }

        // Generate OTP Event
        if (generateBtn) {
            generateBtn.onclick = function () {
                clearMessage();

                const emailInput = document.querySelector(".cmp-otp__dynamic-field[type='email'], #email");
                const captchaInput = document.getElementById("captchaInputStage1");

                if (!emailInput || !emailInput.value.trim()) {
                    showMessage("Please enter a valid email address.", "error");
                    return;
                }

                if (!captchaInput || !captchaInput.value || captchaInput.value.trim().toUpperCase() !== captchaCode1) {
                    showMessage("Invalid CAPTCHA code. Please try again.", "error");
                    captchaCode1 = drawCaptcha("captchaCanvasStage1");
                    return;
                }

                generateBtn.disabled = true;
                generateBtn.textContent = "Sending...";

                fetch(generateUrl, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded"
                    },
                    body: new URLSearchParams({ "email": emailInput.value.trim() })
                })
                .then(res => {
                    if (!res.ok) throw new Error("HTTP Status: " + res.status);
                    return res.json();
                })
                .then(data => {
                    generateBtn.disabled = false;
                    generateBtn.textContent = "Send OTP";

                    if (data.status === "success") {
                        stage1?.classList.add("cmp-otp__stage--hidden");
                        stage2?.classList.remove("cmp-otp__stage--hidden");
                        showMessage(data.message || "OTP sent successfully!", "success");
                        startTimer();
                    } else {
                        showMessage(data.message || "Failed to send OTP.", "error");
                    }
                })
                .catch(err => {
                    generateBtn.disabled = false;
                    generateBtn.textContent = "Send OTP";
                    showMessage("Error sending OTP.", "error");
                    console.error(err);
                });
            };
        }

        // Verify OTP Event
        if (verifyBtn) {
            verifyBtn.onclick = function () {
                clearMessage();

                const otpInput = document.getElementById("otpInput");
                const captchaInput = document.getElementById("captchaInputStage2");

                if (!otpInput || !otpInput.value.trim()) {
                    showMessage("Please enter the OTP.", "error");
                    return;
                }

                if (!captchaInput || !captchaInput.value || captchaInput.value.trim().toUpperCase() !== captchaCode2) {
                    showMessage("Invalid CAPTCHA code.", "error");
                    captchaCode2 = drawCaptcha("captchaCanvasStage2");
                    return;
                }

                verifyBtn.disabled = true;
                verifyBtn.textContent = "Verifying...";

                fetch(verifyUrl, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded"
                    },
                    body: new URLSearchParams({ "otp": otpInput.value.trim() })
                })
                .then(res => res.json())
                .then(data => {
                    verifyBtn.disabled = false;
                    verifyBtn.textContent = "Verify OTP";

                    if (data.status === "success") {
                        showMessage(data.message || "OTP verified successfully!", "success");
                        clearInterval(timerInterval);
                        stage2?.classList.add("cmp-otp__stage--hidden");
                    } else {
                        showMessage(data.message || "Invalid OTP.", "error");
                    }
                })
                .catch(err => {
                    verifyBtn.disabled = false;
                    verifyBtn.textContent = "Verify OTP";
                    showMessage("Verification error.", "error");
                    console.error(err);
                });
            };
        }

        if (resendBtn) {
            resendBtn.onclick = function () {
                stage2?.classList.add("cmp-otp__stage--hidden");
                stage1?.classList.remove("cmp-otp__stage--hidden");
                captchaCode1 = drawCaptcha("captchaCanvasStage1");
                clearMessage();
            };
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initOtpComponent);
    } else {
        initOtpComponent();
    }

    document.addEventListener("cq-contentloaded", initOtpComponent);
})();