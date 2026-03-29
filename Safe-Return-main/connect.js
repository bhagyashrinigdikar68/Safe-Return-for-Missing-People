async function submitPublicLostReport() {

  const phoneNumber = document.getElementById("public-familyPhone").value.trim();
  const indianPhoneRegex = /^[6-9]\d{9}$/;

  if (!indianPhoneRegex.test(phoneNumber)) {
    alert("Please enter a valid 10-digit Indian mobile number (starts with 6-9)");
    return;
  }

  const dateTimeValue = document.getElementById("public-dateTime").value;
  if (!dateTimeValue) {
    alert("Please select last seen date & time.");
    return;
  }

  const selectedDate = new Date(dateTimeValue);
  const currentDate = new Date();
  if (selectedDate > currentDate) {
    alert("Future date is not allowed. Please select a past date & time.");
    return;
  }

  const formData = new FormData();
  formData.append("public-fullName",        document.getElementById("public-fullName").value);
  formData.append("public-age",             document.getElementById("public-age").value);
  formData.append("gender",                 document.getElementById("gender").value);
  formData.append("language_spoken",        document.getElementById("language_spoken").value);
  formData.append("public-location",        document.getElementById("public-location").value);
  formData.append("public-dateTime",        document.getElementById("public-dateTime").value);
  formData.append("clothing_description",   document.getElementById("clothing_description").value);
  formData.append("general_description",    document.getElementById("general_description").value);
  formData.append("medical_condition",      document.getElementById("medical_condition").value);
  formData.append("public-familyName",      document.getElementById("public-familyName").value);
  formData.append("public-familyPhone",     phoneNumber);

  const photoInput = document.getElementById("publicPhotoInput");
  if (photoInput.files.length > 0) {
    formData.append("photo", photoInput.files[0]);
  }

  const response = await fetch("http://127.0.0.1:5001/submit", {
    method: "POST",
    body: formData
  });

  const result = await response.json();

  if (!response.ok) {
    alert(result.error);
    return;
  }

  alert(result.message);

  currentUserRole = "Public";
  showDashboard();
}

// ================================
// LOAD ADMIN REPORTS FROM MONGODB
// ← Fixed: port 5001 (app.py), correct field mapping, photo URL fix
// ================================
async function loadAdminReports() {
    try {
        const res = await fetch("http://127.0.0.1:5001/get-reports");  // ← 5001 not 5000
        if (!res.ok) throw new Error("HTTP " + res.status);
        const data = await res.json();

        adminReports = data.map(function(r) {
            // Fix photo path — works for both relative and absolute Windows paths
            var rawPhoto = r.photo_path || '';
            var photoUrl = null;
            if (rawPhoto) {
                var filename = rawPhoto.replace(/\\/g, '/').split('/').pop();
                photoUrl = 'http://127.0.0.1:5001/uploads/photos/' + filename;
            }
            return {
                id:          r._id,
                name:        r.full_name           || 'Unknown',
                age:         r.age                 || '—',
                gender:      r.gender              || 'Unknown',
                loc:         r.last_seen_location  || '—',
                date:        (r.last_seen_datetime || '').slice(0, 10),
                status:      r.status              || 'Missing',
                photo:       photoUrl,
                familyPhone: r.contact_phone       || '',
                source:      'db'
            };
        });

        renderAdminTable();
    } catch (err) {
        console.error("Error loading reports:", err);
    }
}

// ================================
// DATE MAX VALIDATION
// ================================
window.addEventListener("DOMContentLoaded", function () {
    const el = document.getElementById("public-dateTime");
    if (el) {
        const now = new Date();
        now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
        el.max = now.toISOString().slice(0, 16);
    }
});

// ================================
// AUTO LOAD ON PAGE LOAD
// ================================
document.addEventListener("DOMContentLoaded", function () {
    const adminPage = document.getElementById("adminDashboard");
    if (adminPage) {
        loadAdminReports();
    }
});