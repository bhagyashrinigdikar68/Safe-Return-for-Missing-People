async function submitPublicLostReport() {
    
      currentUserRole = 'Public';

  // ── 1. Primary phone validation (unchanged) ──────────────────
  const phoneNumber = document.getElementById("public-familyPhone").value.trim();
  const indianPhoneRegex = /^[6-9]\d{9}$/;
  if (!indianPhoneRegex.test(phoneNumber)) {
    alert("Please enter a valid 10-digit Indian mobile number (starts with 6-9)");
    return;
  }

  // ── 2. Date validation (unchanged) ───────────────────────────
  const dateTimeValue = document.getElementById("public-dateTime").value;
  if (!dateTimeValue) {
    alert("Please select last seen date & time.");
    return;
  }
  const selectedDate = new Date(dateTimeValue);
  const currentDate  = new Date();
  if (selectedDate > currentDate) {
    alert("Future date is not allowed. Please select a past date & time.");
    return;
  }

  // ── 3. Read NEW fields from their exact HTML ids ──────────────
  const famEmailEl    = document.getElementById("public-familyEmail");
  const altPhoneEl    = document.getElementById("public-familyPhone2");
  const famAadhaarEl  = document.getElementById("public-familyAadhaar");
  const perAadhaarEl  = document.getElementById("public-personAadhaar");

  const famEmail   = famEmailEl   ? famEmailEl.value.trim()   : "";
  const altPhoneRaw= altPhoneEl   ? altPhoneEl.value.trim()   : "";
  const famAadhaar = famAadhaarEl ? famAadhaarEl.value.trim() : "";
  const perAadhaar = perAadhaarEl ? perAadhaarEl.value.trim() : "";

  // Strip non-digits from alternate phone before sending
  const altDigits = altPhoneRaw.replace(/\D/g, "");

  // ── 4. Validate NEW fields (all optional) ────────────────────
  if (altDigits && !/^[6-9]\d{9}$/.test(altDigits)) {
    alert("Alternate phone is invalid. Must be 10 digits starting with 6-9.");
    return;
  }
  if (famAadhaar && !/^[2-9]\d{11}$/.test(famAadhaar)) {
    alert("Family Aadhaar is invalid. Must be 12 digits, cannot start with 0 or 1.");
    return;
  }
  if (perAadhaar && !/^[2-9]\d{11}$/.test(perAadhaar)) {
    alert("Person Aadhaar is invalid. Must be 12 digits, cannot start with 0 or 1.");
    return;
  }

  // ── 5. Build FormData ─────────────────────────────────────────
  const formData = new FormData();

  // Original 11 fields (unchanged)
  formData.append("public-fullName",       document.getElementById("public-fullName").value);
  formData.append("public-age",            document.getElementById("public-age").value);
  formData.append("gender",                document.getElementById("gender").value);
  formData.append("language_spoken",       document.getElementById("language_spoken").value);
  formData.append("public-location",       document.getElementById("public-location").value);
  formData.append("public-dateTime",       document.getElementById("public-dateTime").value);
  formData.append("clothing_description",  document.getElementById("clothing_description").value);
  formData.append("general_description",   document.getElementById("general_description").value);
  formData.append("medical_condition",     document.getElementById("medical_condition").value);
  formData.append("public-familyName",     document.getElementById("public-familyName").value);
  formData.append("public-familyPhone",    phoneNumber);

  // ✅ NEW: 4 fields — THIS was the missing piece causing fields not to save
  //    FormData key           →  app.py data.get(key)    →  MongoDB field
  formData.append("public-familyEmail",    famEmail);    // → contact_email
  formData.append("public-familyPhone2",   altDigits);   // → alternate_phone
  formData.append("public-familyAadhaar",  famAadhaar);  // → family_aadhaar
  formData.append("public-personAadhaar",  perAadhaar);  // → person_aadhaar

  // Photo
  const photoInput = document.getElementById("publicPhotoInput");
  if (photoInput && photoInput.files.length > 0) {
    formData.append("photo", photoInput.files[0]);
  }

  // ── 6. POST to Flask (unchanged) ─────────────────────────────
  const response = await fetch("http://127.0.0.1:5001/submit", {
    method: "POST",
    body:   formData
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


// ================================================================
//  LOAD ADMIN REPORTS FROM MONGODB
//  Updated: maps 4 new MongoDB fields into adminReports[]
// ================================================================
async function loadAdminReports() {
  try {
    const res = await fetch("http://127.0.0.1:5001/get-reports");
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();

    adminReports = data.map(function(r) {

      // Photo URL — handles both new relative path and old absolute Windows path
      var rawPhoto = r.photo_path || "";
      var photoUrl = null;
      if (rawPhoto) {
        var filename = rawPhoto.replace(/\\/g, "/").split("/").pop();
        photoUrl = "http://127.0.0.1:5001/uploads/photos/" + filename;
      }

      return {
        // Original fields (unchanged)
        id:          r._id,
        name:        r.full_name          || "Unknown",
        age:         r.age                || "—",
        gender:      r.gender             || "Unknown",
        loc:         r.last_seen_location || "—",
        date:        (r.last_seen_datetime || "").slice(0, 10),
        status:      r.status             || "Missing",
        photo:       photoUrl,
        familyPhone: r.contact_phone      || "",
        // ✅ NEW: 4 fields read from MongoDB
        //    MongoDB key          JS key used in viewAdminDetail / showAadhaarMatchPopup
        familyEmail:      r.contact_email    || "",
        alternatePhone:   r.alternate_phone  || "",
        famAadhaar:       r.family_aadhaar   || "",
        perAadhaar:       r.person_aadhaar   || "",
        familyContactName: r.contact_name   || "",
        source: "db"
      };
    });

    renderAdminTable();
  } catch (err) {
    console.error("Error loading reports:", err);
  }
}


// ================================================================
//  DATE MAX VALIDATION (unchanged)
// ================================================================
window.addEventListener("DOMContentLoaded", function () {
  const el = document.getElementById("public-dateTime");
  if (el) {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    el.max = now.toISOString().slice(0, 16);
  }
});


// ================================================================
//  AUTO LOAD ON PAGE LOAD (unchanged)
// ================================================================
document.addEventListener("DOMContentLoaded", function () {
  const adminPage = document.getElementById("adminDashboard");
  if (adminPage) {
    loadAdminReports();
  }
});