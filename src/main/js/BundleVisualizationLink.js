/* globals notificationBar: true */

import {copyString} from './lib/Clipboard';

const EVENT_DELEGATION_CONTAINER_ID = "casc-bundle-files-table";

/**
 * Checks if a string is empty
 * @param {*} str 
 */
function isEmptyString(str) {
    return !str || str instanceof String && str.trim() === '';
}

/**
 * Copy the content of a remote file into the clipboard
 * 
 * @param {HTMLElement} url - the url to the file
 * @param {String} successMessage - optional. notification displayed when the file is copied successfully
 * @param {String} emptyFileMessage - optional. error notification displayed when the file is empty and not copied
 * @param {String} errorMessage - optional. error notification displayed when the file can not be downloaded
 * @return {Promise|undefined} promise of the download of the file. undefined if the linkElement doesn't have a href attribute
 */
function copyContentFile(url, {successMessage, emptyFileMessage, errorMessage}){
    if (!url) return;

    const request = new XMLHttpRequest();
    request.open('GET', url);
    request.send();
    request.onerror = () => {
        //Connectivity issues
        errorMessage && notificationBar.show(`${errorMessage}`, notificationBar.ERROR);
    }
    request.onload = () => {
        //Failed?
        if (request.status !== 200) {
            errorMessage && notificationBar.show(`${errorMessage} (${request.statusText})`, notificationBar.ERROR);
            return;
        }
        //Is the file empty?
        const content = request.responseText;
        if(isEmptyString(content)) {
          emptyFileMessage && notificationBar.show(emptyFileMessage, notificationBar.ERROR);
          return;
        }
        // Copy the content into the clipboard
        copyString(content);
        successMessage && notificationBar.show(successMessage, notificationBar.OK);
    }
}



//When the page is loaded
window.addEventListener('DOMContentLoaded', function CascManagementOnLoad (){
    var table = document.getElementById(EVENT_DELEGATION_CONTAINER_ID);
    if(!table) return;

    // simple event delegation
    table.addEventListener('click', event => {
        const target = event.target.closest('[data-action="copy-in-clipboard"]');
        if(target) {
            event.stopPropagation();
            event.preventDefault ? event.preventDefault() : (event.returnValue = false);  //preventDefault does not exist in IE < 9

            //Messages parametrized in data- attributes
            copyContentFile(target.href, target.dataset);
        }
    });
});