// Runs updateActive based on current page
function updateActiveOnPage(page) { 
    if (page == "/" || page == '/synthea/') { 
        updateActive(".navbar-nav", 100, 50);
    }
}

// Update the active location in the nav bar based on the navbar name, 
// how far above an element counts as a new-element trigger, and how far above 
// the bottom of the page counts as the bottom-of-the-page trigger  
function updateActive(navName, elemOffset, bottomOffset) {
    var linkTops = [];
    var wTop     = $(window).scrollTop();
    var rangeTop = 15;
    const scrollMod = (navName == ".navbar-nav") ? ".scroll" : "";
    const navLink = navName + " li" + scrollMod;
    const anchor = scrollMod + ' a';
    $(navName).find(anchor).each(function(){
        linkTops.push($(this.hash).offset().top - elemOffset);
    });
    if ($(window).scrollTop() + $(window).height() + bottomOffset >= $(document).height()) {
        $(navLink)
            .removeClass('active')
            .eq(linkTops.length -1).addClass('active');        
    } else {
        $.each( linkTops, function(i) {
            if ( wTop > linkTops[i] - rangeTop ){
                $(navLink)
                    .removeClass('active')     // Drop any active elems (of which there are one)
                    .eq(i).addClass('active'); // Add active to the current element                 
            }
        });
    }
};


// Used to equalize height across eq-h-rows
function eqHeight(){
    $('.eq-h-row').each(function() {
        // Try to autofit the height
        var elemsMd = $(this).find('.eq-h-md');
        elemsMd.height('auto');

        // If the viewport is large, find biggest innerheight and normalize to that
        if ($(window)[0].innerWidth >= 992) {
            var maxH = 0;
            elemsMd.each(function() {
                // find max inner height
                maxH = $(this).innerHeight() > maxH ? $(this).innerHeight() : maxH;
            });
            // Change accordingly 
            elemsMd.innerHeight(maxH + 50);
        }
        var elemsSm = $(this).find('.eq-h-sm');
        elemsSm.height('auto');

        // If the viewport is large, find biggest innerheight and normalize to that
        if ($(window)[0].innerWidth >= 768) {
            var maxH = 0;
            elemsSm.each(function() {
                // find max inner height
                maxH = $(this).innerHeight() > maxH ? $(this).innerHeight() : maxH;
            });
            // Change accordingly 
            elemsSm.innerHeight(maxH + 50);
        }

        var elemsXs = $(this).find('.eq-h-xs');
        elemsXs.height('auto');

        if ($(window)[0].innerWidth >= 480) {
            var maxH = 0;
            elemsXs.each(function() {
                // find max inner height
                maxH = $(this).innerHeight() > maxH ? $(this).innerHeight() : maxH;
            });
            // Change accordingly 
            elemsXs.innerHeight(maxH + 50);
        }
    });
} 


// On particular event, close menu if target not an opening elem 
function disableTouchOnEvent(eventType) {
    $(document).on(eventType, function (event) {
        const clickover = $(event.target);
        const _opened = $(".navbar-collapse").hasClass("navbar-collapse collapse in");
        if (_opened && !clickover.hasClass("navbar-toggle") && !clickover.hasClass("dropdown-toggle")) {
            $("button.navbar-toggle").click();
        }
    });
}

function snackbarGeneration() { 
    var d = new Date();
    var eventDate = new Date("1/1/1970");   
    var popupDelay = 1500;      // time until snackbar pops up (ms)
    var options =  {    
        content: "",            // HTML content of the snackbar 
        htmlAllowed: true,      // allows HTML as content value
        timeout: 10000          // time (ms) after the snackbar dismisses, 0 is disabled
    };

    // If HIMSS hasn't ended yet and we're on the home bottom-of-the-page 
    if (window.location.pathname === "/" && (d.getTime() <= eventDate.getTime())) { 
        setTimeout(function() {
            var snackbarId = $.snackbar(options);
            $("#snackbar-container").on("click", function(event) {
                if ($(event.target).hasClass("snackbar-close")) {
                    $("#snackbarId").snackbar("hide")
                } else { 
                    window.open($(this).find('a')[0].href);
                }
            });
        }, popupDelay);
    }
}

// Fades content on the page in after the webpage has loaded
function fadeCoverOut() { 
    $('#loading').fadeOut();
};


// Updates copyright date if a new year has come
function updateDate() { 
    const d = new Date();
    let curYear = $('#curYear').text()
    if (parseInt(curYear) < parseInt(d.getFullYear())) {
      $('#curYear').text(d.getFullYear())
    }
}

// Modify the modal to prev3ent the screen shifting to the left on open
function modalModifications() { 
    $('.modal').on('show.bs.modal', function () {
        if ($(document).height() > $(window).height()) {
            // no-scroll
            $('body').addClass("modal-open-noscroll");
        }
        else {
            setTimeout(function() {
                $('body').removeClass("modal-open-noscroll");
                console.log('firign');
            }, 400);
        }
    });
    $('.modal').on('hide.bs.modal', function () {
        setTimeout(function() {
            $('body').removeClass("modal-open-noscroll");
            console.log('firign');
        }, 400);
    });
}

// When doc is ready...
$(function () {
    // Disable menu when touchend is picked up on non-menu elementes
    disableTouchOnEvent('touchend');
    // Disable menu when click is picked up on non-menu elementes
    disableTouchOnEvent('click');
    // Determine how to update the page based on the current page
    updateActiveOnPage(window.location.pathname);
    // Update the copyright if needed
    updateDate();
    // generate snackbars if appropriate
    snackbarGeneration();
    // modifies modals if appropriate
    modalModifications();
});

//  When everything has loaded fully
$(window).load(function(){
    // Load page if applicable
    setTimeout(fadeCoverOut, 1800);
    // Stablize heights
    eqHeight();
});

// When window is resizing...
$(window).resize(function () { 
    // Stablize heights on doc.ready and resizing
    eqHeight();
});


// When window is scrolling...
$(window).scroll(function(event) {    
    // Update active based on page
    updateActiveOnPage(window.location.pathname);
}); 

