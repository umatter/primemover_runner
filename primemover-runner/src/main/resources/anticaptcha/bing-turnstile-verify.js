// Hand a Turnstile token to Bing's challenge page the way the page's own
// success callback would, minus the reload (the runner reloads, so that it can
// see the result). Run with executeAsyncScript(script, verifyEndpoint, token,
// action, partner); answers {status, success[, error]}.
//
// What Bing's callback does, from its inline script (experiment 199, job
// 715588): read cf-turnstile-response, GET verifyEndpoint + token +
// "&clientAction=" + encodeURIComponent(ruleId) with credentials, accept
// {"Success": true}; report the outcome to web/chlrate ("s" on success, "vf"
// on a refused token, which also sets the _SS "fldcp" cookie key); set the _SS
// "fr" key either way; reload. Each step is copied here, so the requests Bing
// sees are the ones a solved widget would have sent.
var endpoint = arguments[0];
var token = arguments[1];
var action = arguments[2];
var partner = arguments[3];
var done = arguments[arguments.length - 1];

var inputs = document.getElementsByName("cf-turnstile-response");
for (var i = 0; i < inputs.length; i++) {
	inputs[i].value = token;
}

function chlrate(status) {
	try {
		var xhr = new XMLHttpRequest();
		xhr.open("POST", encodeURI("web/chlrate?status=" + status + "&partner=" + partner), true);
		xhr.timeout = 2000;
		xhr.setRequestHeader("content-type", "application/json");
		xhr.send();
	} catch (e) {
		// telemetry only
	}
}

function setSessionCookie(key, value) {
	try {
		if (window.sj_cook && typeof window.sj_cook.set === "function") {
			window.sj_cook.set("_SS", key, value);
		}
	} catch (e) {
		// the page's helper is missing; the verify call is what counts
	}
}

fetch(endpoint + token + "&clientAction=" + encodeURIComponent(action), {
	method: "GET",
	headers: { Accept: "application/json" },
	credentials: "include"
}).then(function (response) {
	if (!response.ok) {
		done({ status: response.status, success: false });
		return;
	}
	return response.json().then(function (body) {
		var success = !!body && body.Success === true;
		if (success) {
			chlrate("s");
		} else {
			chlrate("vf");
			setSessionCookie("fldcp", partner);
		}
		setSessionCookie("fr", "1");
		done({ status: response.status, success: success });
	});
}).catch(function (e) {
	done({ status: 0, success: false, error: String(e) });
});
