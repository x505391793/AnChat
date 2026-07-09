function isEmptyStr(str) {
    if (str == null) {
        return true;
    } else {
        str = $.trim(str);
        if (str === '') {
            return true;
        }
    }
    return false;
}


function sendGet(url,params){

}



function sendPostForm(url, target, params) {
    let temp = document.createElement("form");
    temp.action = url;
    temp.method = 'POST';
    temp.target = target;
    if (document.charset) {
        document.charset = 'utf-8'; //设置提交的编码为gbk  IE不能识别form.accept-charset
    } else {
        temp.setAttribute('accept-charset', 'utf-8'); //设置编码gbk，不能够解析document.charset的浏览器，用form.accept-charset参数
    }
    for (let x in params) {
        let opt = document.createElement('input');
        opt.type = 'hidden';
        opt.name = x;
        opt.value = params[x];
        temp.appendChild(opt);
    }
    document.body.appendChild(temp);
    temp.submit();
    document.body.removeChild(temp);
}


function formatDate(date, format) {
    if (date == null || date == "") {
        return "";
    }
    if (typeof (date) != "Date") {
        date = new Date(date);
    }
    var paddNum = function (num) {
        num += "";
        return num.replace(/^(\d)$/, "0$1");
    }
    //指定格式字符
    var cfg = {
        yyyy: date.getFullYear(), //年 : 4位
        yy: date.getFullYear().toString().substring(2),//年 : 2位
        M: date.getMonth() + 1,  //月 : 如果1位的时候不补0
        MM: paddNum(date.getMonth() + 1), //月 : 如果1位的时候补0
        d: date.getDate(),   //日 : 如果1位的时候不补0
        dd: paddNum(date.getDate()),//日 : 如果1位的时候补0
        hh: date.getHours(),  //时
        mm: date.getMinutes(), //分
        ss: date.getSeconds() //秒
    }
    format || (format = "yyyy-MM-dd hh:mm:ss");
    return format.replace(/([a-z])(\1)*/ig, function (m) {
        return cfg[m];
    });
}

function getFormParams(form) {
    let params = {};//给obj分配内存
    let strData = $(form).serializeArray();
    for (let i = 0; i < strData.length; i++) {
        params[strData [i].name] = strData [i]['value'];
    }
    return params;
}


function hasAttr(obj, attr) {
    return typeof ($(obj).attr(attr)) !== 'undefined';
}

function getAttr(obj, attr, def) {
    let val = $(obj).attr(attr);
    return typeof (val) !== 'undefined' ? val : def;
}


function getFormData($form) {
    let array = $form.serializeArray();
    let formDate = new FormData;
    $.map(array, function (n, i) {
        if (n['name'] !== '') {
            let name = n['name'];
            let obj = $form.find('[name="' + name + '"]');
            let val = n['value'];
            if ($(obj).hasClass('selectpicker')) {
                val = $(obj).selectpicker('val');
                if (val instanceof Array) {
                    val = val.join(',');
                }
            }
            formDate.append(name,$.trim(val)) ;
        }
    });
    return formDate;
}









