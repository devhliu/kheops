<template>
  <div
    v-if="UI.show"
    class="chat-popup container-fluid p-0"
  >
    <div
      class="closeBtn d-flex"
    >
      <div
        v-if="sending === true && UI.getInfo === false"
        class="p-2"
      >
        <kheops-clip-loader
          :loading="sending"
          :size="'20px'"
          :color="'white'"
        />
      </div>
      <div
        v-else-if="sending === false && UI.getInfo === false"
        class="p-2"
      >
        <done-icon
          v-if="error.length === 0 && totalUnknownFilesError === 0"
          :height="'20'"
          :width="'20'"
        />
        <v-icon
          v-if="(error.length > 0 || totalUnknownFilesError > 0) && (error.length + totalUnknownFilesError) < totalSize"
          name="warning"
          :height="'20'"
          :width="'20'"
          color="red"
        />
        <error-icon
          v-if="(error.length === totalSize || totalUnknownFilesError === totalSize) && totalSize !== 0"
          :height="'20'"
          :width="'20'"
          color="red"
        />
      </div>
      <div
        v-else-if="UI.getInfo === true"
        class="p-2 text-danger"
      >
        <v-icon
          class="align-middle"
          name="warning"
          :height="'20'"
          :width="'20'"
        />
      </div>
      <div
        class="p-2"
      >
        <span
          v-if="sending === true && UI.getInfo === false"
        >
          {{ $t("upload.titleBoxSending") }}
        </span>
        <span
          v-else-if="sending === false && UI.getInfo === false"
        >
          {{ $t("upload.titleBoxSended") }}
        </span>
        <span
          v-else-if="UI.getInfo === true"
        >
          {{ $t("upload.titleBoxDicomize") }}
        </span>
      </div>
      <!--
      <div
        class="ml-auto p-1"
      >
          Reduce / Show icon
        <button
          type="button"
          class="btn btn-link btn-sm"
          @click="UI.hide=!UI.hide"
        >
          <span
            v-if="UI.hide===false"
          >
            <remove-icon
              :height="UI.SVGHeaderHeight"
              :width="UI.SVGHeaderWidth"
            />
          </span>
          <span
            v-if="UI.hide===true"
          >
            <add-icon
              :height="UI.SVGHeaderHeight"
              :width="UI.SVGHeaderWidth"
            />
          </span>
        </button>
      </div>
      -->
      <!--
          Close icon
        -->
      <div
        class="ml-auto p-1"
      >
        <button
          type="button"
          class="btn btn-link btn-sm"
          @click="closeWindow()"
        >
          <close-icon
            :height="UI.SVGHeaderHeight"
            :width="UI.SVGHeaderWidth"
          />
        </button>
      </div>
    </div>
    <div
      v-if="UI.hide === false"
      class="p-2"
    >
      <div
        v-if="UI.getInfo"
        class="mb-2"
      >
        <input-dicomize
          :files-to-dicomize="filesToDicomize"
          :create-study="studyUIDToSend === '' ? true : false"
          @valid-dicom-value="validDicomValue"
        />
      </div>
      <!--
        When sending
      -->
      <div
        v-if="files.length > 0 && sending === true"
      >
        <div
          v-if="UI.cancel === false"
        >
          <b-progress-bar
            :value="progress"
            :max="totalSize"
            show-progress
            animated
            class="text-center"
          >
            {{ countSentFiles }} / {{ totalSize }}
          </b-progress-bar>
          <div
            class="d-flex justify-content-center mt-1 mb-1"
          >
            <button
              type="button"
              class="btn btn-link btn-sm text-center text-warning"
              @click="setCancel()"
            >
              <span>
                {{ $t("cancel") }}
              </span>
              <block-icon
                :height="UI.SVGheight"
                :width="UI.SVGwidth"
                color="red"
              />
            </button>
          </div>
        </div>
        <div
          v-else
        >
          <kheops-clip-loader
            :loading="UI.cancel"
            :size="UI.SpinnerCancelSize"
            :color="'red'"
          />
        </div>
      </div>
      <!--
        When sending finish
      -->
      <div
        v-else-if="(UI.show === true) && (countSentFiles === totalSize || sending === false)"
        class="row"
      >
        <div
          class="col-11 mt-2 mb-2 ml-3"
        >
          {{ $tc("upload.filesSend", countSentFiles - error.length - totalUnknownFilesError, {count: (countSentFiles - error.length - totalUnknownFilesError)}) }}
          {{ $tc("upload.locationSend", sourceIsAlbum ? 0 : 1) }}
          <span
            v-if="sourceIsAlbum"
          >
            <router-link
              :to="{ name: 'album', params: { album_id: sourceSending.value }}"
            >
              {{ $t("upload.album") }}
            </router-link>
          </span>

          <span
            v-if="studyUIDToSend!== ''"
          >
            <!--
              {{ studyUIDToSend }}
            -->
          </span>
          <div
            v-if="Object.keys(listErrorUnknownFiles).length > 0"
          >
            <div
              v-for="(item, key) in listErrorUnknownFiles"
              :key="item.key"
            >
              {{ $tc("upload.unknownError", item, {count: item }) }} <br>
              <span
                class="text-warning"
              >
                {{ generateTextError(key) }}
              </span>
            </div>
          </div>

          <div
            v-if="error.length > 0"
          >
            <div
              class="mb-1"
            >
              <span>
                {{ $tc("upload.filesErrors", error.length, {count: error.length}) }}
              </span>
            </div>
            <div
              class="mb-1"
            >
              <a
                class="text-center text-neutral"
                @click="retry"
              >
                {{ $t('upload.reload') }}
              </a>
            </div>
            <a
              class="text-center text-warning"
              @click="UI.showErrors=!UI.showErrors"
            >
              <span v-if="!UI.showErrors">
                {{ $t("upload.showError") }}
              </span>
              <span v-else>
                {{ $t("upload.hideError") }}
              </span>
              <error-icon
                :height="UI.SVGheight"
                :width="UI.SVGwidth"
                color="red"
              />
            </a>
          </div>
        </div>
      </div>
      <!--
        Unknow files error
      -->

      <!--
        Show the errors
      -->
      <div
        v-if="error.length > 0 && UI.showErrors"
        class="row"
      >
        <div
          class="col-12 mt-2 mb-2"
        >
          <list-error-files
            :error-files="error"
            @show-errors="setShowErrors"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import KheopsClipLoader from '@/components/globalloading/KheopsClipLoader';
import { HTTP } from '@/router/http';
import ListErrorFiles from '@/components/study/ListErrorFiles';
import InputDicomize from '@/components/study/InputDicomize';
import ErrorIcon from '@/components/kheopsSVG/ErrorIcon.vue';
import BlockIcon from '@/components/kheopsSVG/BlockIcon';
import CloseIcon from '@/components/kheopsSVG/CloseIcon';
import DoneIcon from '@/components/kheopsSVG/DoneIcon';
import { DicomOperations } from '@/mixins/dicomoperations';
import { CurrentUser } from '@/mixins/currentuser.js';

export default {
  name: 'SendStudies',
  components: {
    ListErrorFiles, ErrorIcon, KheopsClipLoader, BlockIcon, CloseIcon, DoneIcon, InputDicomize,
  },
  mixins: [DicomOperations, CurrentUser],
  props: {
  },
  data() {
    return {
      UI: {
        show: false,
        hide: false,
        cancel: false,
        showErrors: false,
        SVGheight: '20',
        SVGwidth: '20',
        SVGHeaderHeight: '16',
        SVGHeaderWidth: '16',
        SpinnerCancelSize: '30px',
        getInfo: false,
      },
      maxsize: 10e6,
      maxsend: 99,
      config: {
        formData: {
          headers: {
            Accept: 'application/dicom+json',
          },
          onUploadProgress: (progressEvent) => {
            this.progress = this.countSentFiles + (this.currentFilesLength * (progressEvent.loaded / progressEvent.total));
          },
        },
        dicomizeData: {
          headers: {
            'Content-Type': 'multipart/related; type="application/dicom+json"; boundary=myboundary',
          },
          onUploadProgress: (progressEvent) => {
            this.progress = this.countSentFiles + (this.currentFilesLength * (progressEvent.loaded / progressEvent.total));
          },
        },
      },
      errorValues: {
        0xC122: 'upload.transfersyntaxnotsupported',
        292: 'upload.authorizationerror',
        290: 'upload.sopnotsupported',
        272: 'upload.processingfailure',
        262: 'upload.invalidfields',
        0: 'upload.unknownerror',
      },
      hexErrorValues: [
        {
          pattern: 255,
          value: 0xA7FF,
          error: 'upload.refused',
        },
        {
          pattern: 255,
          value: 0xA9FF,
          error: 'upload.notmatchsop',
        },
        {
          pattern: 4095,
          value: 0xCFFF,
          error: 'upload.cannotunderstand',
        },
      ],
      errorDicom: {
        '0008119A': '00041500',
        '00081198': '00041500',
      },
      dicomTagError: '00081197',
      listErrorUnknownFiles: {},
      totalUnknownFilesError: 0,
      progress: 0,
      copyFiles: [],
      countSentFiles: 0,
      filesToDicomize: [],
      filesFiltered: [],
    };
  },
  computed: {
    ...mapGetters({
      sending: 'sending',
      files: 'files',
      totalSize: 'totalSize',
      error: 'error',
      sourceSending: 'sourceSending',
      studyUIDToSend: 'studyUIDToSend',
    }),
    ...mapGetters('oidcStore', [
      'oidcIsAuthenticated',
    ]),
    totalSizeFiles() {
      return this.copyFiles.reduce((total, file) => total + file.content.size, 0);
    },
    sourceIsAlbum() {
      return (this.sourceSending.key !== 'inbox' && this.sourceSending !== undefined && Object.keys(this.sourceSending).length > 0);
    },
  },
  watch: {
    sending() {
      if (this.sending === true) {
        this.sendFiles();
      }
    },
    files() {
      if (this.files.length === 0 && (this.countSentFiles === this.totalSize || this.UI.cancel === true)) {
        this.UI.cancel = false;
        this.$store.dispatch('setSending', { sending: false });
      }
    },
  },
  created() {
  },
  mounted() {
  },
  destroyed() {
  },
  methods: {
    retry() {
      this.$store.dispatch('setSending', { sending: true });
      this.$store.dispatch('setFiles', { files: this.error });
    },
    closeWindow() {
      this.UI.show = !this.UI.show;
      this.UI.cancel = true;
      this.initFiles();
    },
    setCancel() {
      this.UI.cancel = !this.UI.cancel;
      this.initFiles();
    },
    initFiles() {
      if (this.UI.getInfo) {
        this.UI.getInfo = false;
      }
      if (this.files.length > 0) {
        this.$store.dispatch('initFiles');
      }
    },
    sendFiles() {
      this.initVariablesForSending();
      this.filesToDicomize = this.copyFiles.filter((file) => (file.type.includes('image/jpeg') || file.type.includes('application/pdf')
          || file.type.includes('video/mp4') || file.type.includes('video/mpeg')));

      this.filesFiltered = this.copyFiles.filter((file) => (!file.type.includes('image/jpeg') && !file.type.includes('application/pdf')
          && !file.type.includes('video/mp4') && !file.type.includes('video/mpeg')));

      if (this.filesToDicomize.length > 0 && this.filesFiltered.length === 0) {
        if (this.studyUIDToSend !== '') {
          this.UI.getInfo = true;
          this.UI.hide = false;
        } else {
          this.sendFormData(this.filesToDicomize);
        }
      }

      if (this.filesFiltered.length > 0 && this.filesToDicomize.length === 0) {
        this.sendFormData(this.filesFiltered);
      }

      if (this.filesFiltered.length > 0 && this.filesToDicomize.length > 0) {
        this.sendFormData(this.copyFiles);
      }
    },
    validDicomValue(dicomValue) {
      this.UI.getInfo = false;
      this.sendDicomizeFiles(this.filesToDicomize, dicomValue);
      if (this.filesFiltered.length > 0) {
        this.sendFormData(this.filesFiltered);
      }
    },
    errorDicomize(file, err = undefined) {
      const data = err !== undefined && err.response !== undefined && err.response.data !== undefined ? err.response.data : {};
      const result = this.getErrorsDicomFromResponse(data);
      if (result < 0) {
        const status = this.getStatus(err);
        this.generateErrorNonDicom([file], status);
      }
      this.$store.dispatch('removeFileId', { id: file.id });
    },
    getStatus(resp) {
      if (resp !== undefined) {
        if (resp.status !== undefined) {
          return resp.status;
        }
        if (resp.response !== undefined && resp.response.status !== undefined) {
          return resp.response.status;
        }
      }
      return undefined;
    },
    sendDicomizeFiles(files, dicomValue) {
      let promiseSequential = Promise.resolve();
      this.getStudy(this.studyUIDToSend).then((res) => {
        const study = res.data[0];
        files.forEach((file) => {
          promiseSequential = promiseSequential.then(() => new Promise((resolve, reject) => {
            this.dicomize(study, file, dicomValue[file.name]).then((resdicomize) => {
              const data = resdicomize;
              this.sendDicomizeDataPromise(file.id, data).then(() => {
                this.$store.dispatch('removeFileId', { id: file.id });
                this.countSentFiles += 1;
                resolve({ id: file.id, data });
              }).catch((err) => {
                reject(err);
              });
            }).catch((err) => {
              reject(err);
            });
          })).catch((err) => {
            this.errorDicomize(file, err);
            this.countSentFiles += 1;
          });
        });
      }).catch((err) => {
        files.forEach((file) => {
          this.errorDicomize(file, err);
          this.countSentFiles += 1;
        });
      });
    },
    sendDicomizeDataPromise(idFile, data) {
      return new Promise((resolve, reject) => {
        if (!this.UI.cancel && this.files.length > 0) {
          const formData = new FormData();
          this.currentFilesLength = 1;
          formData.append(idFile, data);
          const request = `/studies${this.sourceIsAlbum ? `?${this.sourceSending.key}=${this.sourceSending.value}` : ''}`;
          const headers = this.setAuthorizationHeader();
          this.config.dicomizeData.headers = { ...this.config.dicomizeData.headers, ...headers };
          HTTP.post(request, data, this.config.dicomizeData).then((res) => {
            resolve(res);
          }).catch((err) => {
            reject(err);
          });
        }
      });
    },
    sendFormData(files) {
      if (this.maxsize > this.totalSizeFiles && files.length <= this.maxsend && files.length > 0) {
        this.sendFormDataPromise(files);
      } else if (files.length > 0) {
        this.sendBySize(files);
      }
    },
    setAuthorizationHeader() {
      const headers = {};
      if (this.getCurrentuserAccessToken(this.oidcIsAuthenticated) !== '') {
        headers.Authorization = `Bearer ${this.getCurrentuserAccessToken(this.oidcIsAuthenticated)}`;
      }
      return headers;
    },
    initVariablesForSending() {
      this.UI.show = true;
      this.UI.cancel = false;
      this.countSentFiles = 0;
      this.copyFiles = _.cloneDeep(this.files);
      this.progress = 0;
      this.listErrorUnknownFiles = {};
      this.totalUnknownFilesError = 0;
      this.$store.dispatch('setSending', { sending: true });
      this.$store.dispatch('initErrorFiles');
    },
    sendBySize(files) {
      const state = {
        size: 0,
        tmpIndex: 0,
      };
      // https://stackoverflow.com/questions/48014050/wait-promise-inside-for-loop
      let promiseChain = Promise.resolve();
      files.forEach(async (file, index) => {
        state.size += file.content.size;
        if (this.maxsize < state.size || ((index - state.tmpIndex) >= this.maxsend)) {
          const nextPromise = this.createNextPromise(files, state.tmpIndex, index + 1);
          promiseChain = promiseChain.then(nextPromise());
          state.tmpIndex = index + 1;
          state.size = 0;
        } else if (index === files.length - 1) {
          const nextPromise = this.createNextPromise(files, state.tmpIndex, files.length);
          promiseChain = promiseChain.then(nextPromise());
        }
      });
    },
    createNextPromise(files, firstIndex, secondIndex) {
      const currentFiles = this.getArrayFilesToSend(files, firstIndex, secondIndex);
      const nextPromise = () => () => this.sendFormDataPromise(currentFiles);
      return nextPromise;
    },
    getArrayFilesToSend(files, firstIndex, secondIndex) {
      if (firstIndex === secondIndex) {
        return [files[secondIndex]];
      }
      return files.slice(firstIndex, secondIndex);
    },
    sendFormDataPromise(files) {
      return new Promise((resolve) => {
        if (!this.UI.cancel && this.files.length > 0) {
          const formData = this.createFormData(files);
          this.currentFilesLength = files.length;
          const headers = this.setAuthorizationHeader();
          this.config.formData.headers = { ...this.config.formData.headers, ...headers };
          const request = `/studies${this.sourceIsAlbum ? `?${this.sourceSending.key}=${this.sourceSending.value}` : ''}`;
          HTTP.post(request, formData, this.config.formData).then((res) => {
            this.manageResult(files, res.data, res.status);
            resolve(res);
          }).catch((error) => {
            this.manageResult(files, error !== undefined && error.response !== undefined && error.response.data !== undefined ? error.response.data : {}, error !== undefined && error.response !== undefined && error.response.status !== undefined ? error.response.status : 0);
            resolve(error);
          });
        } else if (this.files.length > 0) {
          this.$store.dispatch('initFiles');
          resolve('removeFiles');
        } else {
          resolve('noFiles');
        }
      });
    },
    manageResult(files, data, status) {
      if (status !== 200) {
        const result = this.getErrorsDicomFromResponse(data);
        if (result < 0) {
          this.generateErrorNonDicom(files, status);
        }
      }
      this.$store.dispatch('removeFilesId', { files });
      this.countSentFiles += files.length;
    },
    createFormData(files) {
      const formData = new FormData();
      files.forEach((file) => {
        formData.append(file.id, file.content);
      });
      return formData;
    },
    getErrorsDicomFromResponse(data) {
      let error = -1;
      Object.keys(this.errorDicom).forEach((key) => {
        if (Object.prototype.hasOwnProperty.call(data, key)) {
          const errorInResponse = this.dicom2map(data[key].Value, this.errorDicom[key]);
          this.createListUnknownError(data[key].Value, this.errorDicom[key]);
          this.createListError(errorInResponse);
          error = 0;
        }
      });
      return error;
    },
    generateErrorNonDicom(files, status = 0) {
      const map = new Map();
      files.forEach((file) => {
        map.set(file.id, status);
      });
      this.createListError(map);
    },
    createListError(error) {
      error.forEach((errorCode, id) => {
        const fileError = this.copyFiles.find((file) => file.id === id);
        if (fileError) {
          const textError = this.errorValues[errorCode] !== undefined ? `${this.$t(this.errorValues[errorCode])} (${errorCode})` : `${this.$t('upload.errorcode')}: ${errorCode}`;
          this.$store.dispatch('setErrorFiles', { error: this.createObjErrors(fileError, textError) });
        } else {
          this.updateListUnknownError(errorCode);
        }
      });
    },
    createListUnknownError(dicom, dicomTagFile) {
      dicom.forEach((x) => {
        if (!Object.prototype.hasOwnProperty.call(x, dicomTagFile)) {
          const errorCode = x[this.dicomTagError].Value[0];
          this.updateListUnknownError(errorCode);
        }
      });
    },
    updateListUnknownError(errorCode) {
      if (Object.prototype.hasOwnProperty.call(this.listErrorUnknownFiles, errorCode)) {
        this.listErrorUnknownFiles[errorCode] += 1;
      } else {
        this.listErrorUnknownFiles[errorCode] = 1;
      }
      this.totalUnknownFilesError += 1;
    },
    generateTextError(errorCode) {
      const code = parseInt(errorCode, 10);
      if (this.errorValues[code] !== undefined) {
        return `${this.$t(this.errorValues[code])} (${code})`;
      }
      let errorText = '';
      this.hexErrorValues.forEach((error) => {
        // eslint-disable-next-line no-bitwise
        if ((code | error.pattern) === error.value) {
          errorText = `${this.$t(error.error)} (${code})`;
        }
      });
      if (errorText !== '') {
        return errorText;
      }
      return `${this.$t('upload.unknownerror')} (${errorCode})`;
    },
    dicom2map(dicom, dicomTagFile) {
      const map = new Map();
      dicom.forEach((x) => {
        if (Object.prototype.hasOwnProperty.call(x, dicomTagFile)) {
          const errorCode = x[this.dicomTagError].Value[0];
          const idFile = x[dicomTagFile].Value[0];
          map.set(idFile, errorCode);
        }
      });
      return map;
    },
    createObjErrors(file, value) {
      const objError = file;
      objError.value = value;
      return objError;
    },
    setShowErrors(value) {
      this.UI.showErrors = value;
    },
  },
};
</script>
